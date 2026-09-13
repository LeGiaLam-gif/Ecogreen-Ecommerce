import axios from 'axios';

// Backend base URL (Spring Boot runs on 8081 by default - see backend/application.properties)
export const API_BASE_URL = 'http://localhost:8081/api';

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
    const status = error?.response?.status;
    // Only an *authenticated* request (one that carried a bearer token) can
    // genuinely mean "your session is no longer valid" - a 401 from
    // /auth/login itself is just "wrong credentials" and must not log
    // anyone out or redirect them away from the login form.
    const hadAuthHeader = Boolean(error?.config?.headers?.Authorization);
    const sessionExpired = status === 401 && hadAuthHeader;

    if (sessionExpired) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }

    const message =
      error?.response?.data?.message ||
      (sessionExpired && 'Your session has expired. Please log in again.') ||
      (status === 401 && 'Invalid username or password.') ||
      (status === 403 && 'You do not have permission to do this.') ||
      (error?.code === 'ERR_NETWORK' && 'Cannot reach the server. Please try again.') ||
      'Something went wrong. Please try again.';
    return Promise.reject({ ...error, friendlyMessage: message });
  }
);

export default http;
