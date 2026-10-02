import axios from 'axios';

// Backend base URL (proxied via Vite server to Spring Boot on port 8081)
export const API_BASE_URL = '/api';

const http = axios.create({ baseURL: API_BASE_URL });

// Attach the bearer token (if any) to every request automatically.
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Centralised error normalisation so pages can show a friendly message
// instead of raw axios/console errors.
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error?.response?.data?.message ||
      (error?.response?.status === 401 && 'Vui lòng đăng nhập để tiếp tục.') ||
      (error?.response?.status === 403 && 'Bạn không có quyền thực hiện thao tác này.') ||
      (error?.code === 'ERR_NETWORK' && 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại kết nối mạng.') ||
      'Đã xảy ra lỗi. Vui lòng thử lại sau.';
    return Promise.reject({ ...error, friendlyMessage: message });
  }
);

export default http;
