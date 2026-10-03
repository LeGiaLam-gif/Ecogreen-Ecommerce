import axios from 'axios';

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

// Attach the bearer token (if any) to every request automatically.
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
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

// Centralised error normalisation so pages can show a friendly message
// instead of raw axios/console errors.
http.interceptors.response.use(unwrapV1, normaliseError);

export default http;
