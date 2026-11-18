import http from './http';

// All auth traffic uses /api/v1/auth (baseURL is '/api'). http.js unwraps {data, meta}, so `res.data` is the payload.

export const login = (username, password, remember = true) =>
  http.post('/v1/auth/login', { username, password }, { headers: { 'X-Remember-Me': String(remember) } })
    .then((res) => res.data);

// Resolves with the created user; the page then sends the person to /login (no auto-login).
export const register = (username, email, password) =>
  http.post('/v1/auth/register', { username, email, password }).then((res) => res.data);

// Sends ONLY the Google ID token; the backend verifies it and derives the identity itself.
export const loginWithGoogle = (idToken, remember = true) =>
  http.post('/v1/auth/google', { idToken }, { headers: { 'X-Remember-Me': String(remember) } })
    .then((res) => res.data);

// Sends ONLY the Facebook access token; the backend asks Facebook who it belongs to.
export const loginWithFacebook = (accessToken, remember = true) =>
  http.post('/v1/auth/facebook', { accessToken }, { headers: { 'X-Remember-Me': String(remember) } })
    .then((res) => res.data);

// Always succeeds (200) for a well-formed e-mail, whether or not an account exists; the backend sends the mail if it does.
export const forgotPassword = (email) =>
  http.post('/v1/auth/forgot-password', { email }).then((res) => res.data);

// `token` is the value from the link in the e-mail.
export const resetPassword = (token, password) =>
  http.post('/v1/auth/reset-password', { token, password }).then((res) => res.data);

// Auth cookies are HttpOnly; the browser sends them automatically with credentialed requests.
export const logout = () => http.post('/v1/auth/logout');

export const getMe = () => http.get('/v1/auth/me').then((res) => res.data);
