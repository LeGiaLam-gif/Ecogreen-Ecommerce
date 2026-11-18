import axios from 'axios';
import { clearSession, hasSessionHint, isRememberedSession } from './authStorage';

// Backend base URL (proxied via Vite server to Spring Boot on port 8081)
export const API_BASE_URL = '/api';

const http = axios.create({ baseURL: API_BASE_URL, withCredentials: true });

/**
 * Dual-mode (B01-F1):
 *  - URLs under /api/v1/** follow the project-wide contract: success `{data, meta}`, errors `{error:{code,message,fields}}`.
 *  - Every other URL is a legacy /api/** endpoint and behaves exactly as before (bare JSON, `{message}` errors).
 * Because baseURL is '/api', callers write v1 endpoints as `http.get('/v1/products')`.
 */
const V1_PATH = /^\/api\/v1(\/|$)/;

// Resolves the absolute path of a request (baseURL + url), whatever form the caller used.
const requestPath = (config) => {
  if (!config) return '';
  const url = config.url || '';
  try {
    if (/^https?:\/\//i.test(url)) return new URL(url).pathname;
    const base = config.baseURL || '';
    const joined = base ? `${base.replace(/\/+$/, '')}/${url.replace(/^\/+/, '')}` : url;
    return new URL(joined, 'http://localhost').pathname;
  } catch {
    return '';
  }
};

export const isV1Request = (config) => V1_PATH.test(requestPath(config));

const isPlainObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

const csrfClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true });
let csrfToken = null;
let csrfPromise = null;
const isStateChanging = (method) => !['get', 'head', 'options', 'trace'].includes((method || 'get').toLowerCase());

const withCsrf = async (config) => {
  if (!isStateChanging(config.method)) return config;
  if (!csrfToken) {
    if (!csrfPromise) {
      csrfPromise = csrfClient.get('/v1/auth/csrf')
        .then((response) => {
          csrfToken = response.data?.data;
          if (!csrfToken) throw new Error('INVALID_CSRF_RESPONSE');
        })
        .finally(() => {
          csrfPromise = null;
        });
    }
    await csrfPromise;
  }
  config.headers = config.headers || {};
  config.headers['X-CSRF-Token'] = csrfToken;
  return config;
};

// The double-submit token is not an authentication credential; access/refresh tokens stay HttpOnly.
http.interceptors.request.use(withCsrf);

// Success: unwrap `{data, meta}` for /api/v1 only.
//   single resource            -> response.data = <data>
//   paged list (meta present)  -> response.data = { items: <data>, meta }
// Legacy responses and v1 responses without an envelope (e.g. 204) are left untouched.
const unwrapV1 = (response) => {
  if (!isV1Request(response.config)) return response;
  const body = response.data;
  if (isPlainObject(body) && Object.prototype.hasOwnProperty.call(body, 'data')) {
    response.data = body.meta != null ? { items: body.data, meta: body.meta } : body.data;
  }
  return response;
};

// Failure: always produce `friendlyMessage`; for /api/v1 also expose `code`, `fields` and `traceId`.
const normaliseError = (error) => {
  const status = error?.response?.status;
  const apiError = isV1Request(error?.config) ? error?.response?.data?.error : null;

  // Spring answers a blocked cross-origin call with a plain-text 403 "Invalid CORS request" (no JSON envelope).
  const corsRejected = status === 403 && typeof error?.response?.data === 'string' && /invalid cors request/i.test(error.response.data);

  const message =
    (corsRejected &&
      'Máy chủ từ chối địa chỉ trang web này (CORS). Hãy mở ứng dụng bằng địa chỉ đã được cấp phép (ví dụ http://localhost:5173) hoặc thêm địa chỉ vào CORS_ALLOWED_ORIGINS của backend.') ||
    (apiError && apiError.message) ||
    error?.response?.data?.message ||
    (status === 401 && 'Vui lòng đăng nhập để tiếp tục.') ||
    (status === 403 && 'Bạn không có quyền thực hiện thao tác này.') ||
    (error?.code === 'ERR_NETWORK' && 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại kết nối mạng.') ||
    'Đã xảy ra lỗi. Vui lòng thử lại sau.';

  const normalised = { ...error, friendlyMessage: message };
  if (apiError) {
    normalised.axiosCode = error.code; // keep axios' own code (e.g. ERR_BAD_REQUEST)
    normalised.code = apiError.code; // API error code (VALIDATION_ERROR, NOT_FOUND, ...)
    normalised.fields = apiError.fields || null;
    normalised.traceId = apiError.traceId || null;
  }
  return Promise.reject(normalised);
};

// ── HttpOnly-cookie refresh ──────────────────────────────────────────────────

// These endpoints never trigger a refresh (a 401 there is the real answer).
const NO_REFRESH_PATH = /^\/api\/v1\/auth\/(login|register|google|facebook|forgot-password|reset-password|refresh|logout)\/?$/;

// A bare axios instance (no interceptors) so a failing refresh can never recurse into itself.
const refreshClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true });

const callRefreshEndpoint = async () => {
  const config = await withCsrf({
    method: 'post',
    headers: { 'X-Remember-Me': String(isRememberedSession()) }
  });
  await refreshClient.post('/v1/auth/refresh', null, config);
};

// Browser cookies are shared across tabs, so serialize refresh rotations when the Lock API is available.
const performRefresh = async () => {
  const run = async () => {
    await callRefreshEndpoint();
  };
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    return navigator.locks.request('ecogreen-token-refresh', run);
  }
  return run();
};

// Single-flight: every concurrent 401 awaits the SAME refresh promise.
let refreshPromise = null;
const refreshOnce = () => {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
};

const redirectToLogin = () => {
  clearSession();
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
};

const shouldRefresh = (error) => {
  const config = error?.config;
  if (!config || config._retry || error?.response?.status !== 401) return false;
  if (!hasSessionHint()) return false;
  if (NO_REFRESH_PATH.test(requestPath(config))) return false;
  const apiCode = error.response?.data?.error?.code; // /api/v1 errors carry a code; legacy 401s do not
  return !apiCode || apiCode === 'UNAUTHENTICATED';
};

const handleResponseError = async (error) => {
  if (shouldRefresh(error)) {
    error.config._retry = true; // at most one refresh + one retry per request
    try {
      await refreshOnce();
    } catch {
      // Any failed refresh ends the session; clear the non-secret hint and prevent another refresh loop.
      redirectToLogin();
      return normaliseError(error);
    }
    return http(error.config); // the browser attaches the rotated HttpOnly cookie
  }
  return normaliseError(error);
};

// Centralised handling: 401 -> single-flight refresh + one retry; everything else -> normalised error with friendlyMessage.
http.interceptors.response.use(unwrapV1, handleResponseError);

export default http;
