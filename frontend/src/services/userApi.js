import http from './http';

export const getAllUsers = () => http.get('/users').then((res) => res.data);

export const deleteUser = (id) => http.delete(`/users/${id}`);
