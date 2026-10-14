import http from './http';

// Staff user management, /api/v1/admin/users (see docs/ai/contracts/B08-customer.md). Lists resolve to { items, meta }.
export const listAdminUsers = ({ page = 0, size = 20, keyword, role, active, sort } = {}, config = {}) =>
  http.get('/v1/admin/users', { params: { page, size, keyword, role, active, sort }, ...config }).then((res) => res.data);

export const getAdminUser = (id) => http.get(`/v1/admin/users/${id}`).then((res) => res.data);

export const getAdminUserOrders = (id, { page = 0, size = 20, status } = {}) =>
  http.get(`/v1/admin/users/${id}/orders`, { params: { page, size, status } }).then((res) => res.data);

export const disableUser = (id) => http.patch(`/v1/admin/users/${id}/disable`).then((res) => res.data);

export const enableUser = (id) => http.patch(`/v1/admin/users/${id}/enable`).then((res) => res.data);

export const setUserRoles = (id, roles) => http.patch(`/v1/admin/users/${id}/roles`, { roles }).then((res) => res.data);

// 409 when the user has orders / return requests, is the last active admin, or is yourself.
export const deleteUser = (id) => http.delete(`/v1/admin/users/${id}`);
