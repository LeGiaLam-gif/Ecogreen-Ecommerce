import http from './http';

export const login = (username, password) =>
  http.post('/auth/login', { username, password }).then((res) => res.data);

export const register = (username, email, password) =>
  http.post('/auth/register', { username, email, password }).then((res) => res.data);

export const loginWithGoogle = (googleData) =>
  http.post('/auth/google', googleData).then((res) => res.data);

export const logout = () => http.post('/auth/logout');

export const getMe = () => http.get('/users/me').then((res) => res.data);

