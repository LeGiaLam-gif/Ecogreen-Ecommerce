import axios from 'axios';
import { clearSession, getAccessToken, getRefreshToken, saveTokens } from './authStorage';

// Backend base URL (proxied via Vite server to Spring Boot on port 8081)
export const API_BASE_URL = '/api';

const http = axios.create({ baseURL: API_BASE_URL });

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

// Attach the access token (JWT, if any) to every request automatically.
http.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

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

  const message =
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

// ── Access-token refresh (B01-P2) ────────────────────────────────────────────

// These endpoints never trigger a refresh (a 401 there is the real answer).
const NO_REFRESH_PATH = /^\/api\/v1\/auth\/(login|register|google|refresh|logout)\/?$/;

// A bare axios instance (no interceptors) so a failing refresh can never recurse into itself.
const refreshClient = axios.create({ baseURL: API_BASE_URL });

const callRefreshEndpoint = async (refreshToken) => {
  const res = await refreshClient.post('/v1/auth/refresh', { refreshToken });
  const pair = res.data?.data;
  if (!pair?.accessToken || !pair?.refreshToken) throw new Error('INVALID_REFRESH_RESPONSE');
  saveTokens(pair);
  return pair.accessToken;
};

// Runs inside a cross-tab lock when available. If another tab already rotated the token while we waited for the
// lock, reuse its result instead of presenting an already-rotated refresh token (which would revoke the family).
const performRefresh = async (startedWith) => {
  const run = async () => {
    const current = getRefreshToken();
    if (!current) throw new Error('NO_REFRESH_TOKEN');
    if (current !== startedWith && getAccessToken()) return getAccessToken();
    return callRefreshEndpoint(current);
  };
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    return navigator.locks.request('ecogreen-token-refresh', run);
  }
  return run();
};

// Single-flight: every concurrent 401 awaits the SAME refresh promise.
let refreshPromise = null;
const refreshOnce = (startedWith) => {
  if (!refreshPromise) {
    refreshPromise = performRefresh(startedWith).finally(() => {
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
  if (!getRefreshToken()) return false;
  if (NO_REFRESH_PATH.test(requestPath(config))) return false;
  const apiCode = error.response?.data?.error?.code; // /api/v1 errors carry a code; legacy 401s do not
  return !apiCode || apiCode === 'UNAUTHENTICATED';
};

const handleResponseError = async (error) => {
  if (shouldRefresh(error)) {
    error.config._retry = true; // at most one refresh + one retry per request
    // The request was sent with an access token that has since been replaced (a concurrent refresh finished first):
    // just retry with the current one instead of rotating the refresh token again.
    const sent = error.config.headers?.Authorization;
    if (sent && getAccessToken() && sent !== `Bearer ${getAccessToken()}`) {
      return http(error.config);
    }
    try {
      await refreshOnce(getRefreshToken());
    } catch {
      // ANY failed refresh (401/403, 5xx, network error, malformed response, no refresh token) ends the session:
      // clear storage and go to /login (B01-P2 spec). After clearing there is no refresh token left, so
      // shouldRefresh() is false for every later 401 and no refresh loop is possible.
      redirectToLogin();
      return normaliseError(error);
    }
    return http(error.config); // request interceptor re-attaches the new access token
  }
  return normaliseError(error);
};

// Centralised handling: 401 -> single-flight refresh + one retry; everything else -> normalised error with friendlyMessage.
http.interceptors.response.use(unwrapV1, handleResponseError);

export default http;
