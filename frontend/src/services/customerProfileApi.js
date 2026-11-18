import http from './http';

export const getCustomerProfile = () => http.get('/v1/users/me').then((res) => res.data);
export const updateCustomerProfile = (payload) => http.put('/v1/users/me', payload).then((res) => res.data);
