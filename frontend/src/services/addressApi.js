import http from './http';

// /api/v1 address book of the logged-in user (see docs/ai/contracts/B08-customer.md).
// The list is not paged (at most 10 addresses): it resolves to a plain array.
export const getAddresses = () => http.get('/v1/users/me/addresses').then((res) => res.data);

export const createAddress = (payload) => http.post('/v1/users/me/addresses', payload).then((res) => res.data);

export const updateAddress = (id, payload) => http.put(`/v1/users/me/addresses/${id}`, payload).then((res) => res.data);

export const setDefaultAddress = (id) => http.patch(`/v1/users/me/addresses/${id}/default`).then((res) => res.data);

export const deleteAddress = (id) => http.delete(`/v1/users/me/addresses/${id}`);
