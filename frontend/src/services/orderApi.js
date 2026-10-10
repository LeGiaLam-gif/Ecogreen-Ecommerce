import http from './http';

// Legacy checkout stays on /api/orders (B05 keeps POST /api/orders; B03 replaces it).
export const checkout = (orderData) => http.post('/orders', orderData).then((res) => res.data);

// /api/v1 endpoints (see docs/ai/contracts/B05-order.md). Lists resolve to { items, meta }.
export const getMyOrders = ({ page = 0, size = 20, status } = {}) =>
  http.get('/v1/orders/my', { params: { page, size, status } }).then((res) => res.data);

export const getOrderById = (id) => http.get(`/v1/orders/${id}`).then((res) => res.data);

export const getOrderHistory = (id) => http.get(`/v1/orders/${id}/history`).then((res) => res.data);

export const cancelOrder = (id, note) =>
  http.post(`/v1/orders/${id}/cancel`, note ? { note } : {}).then((res) => res.data);

export const listAdminOrders = ({ page = 0, size = 20, status, keyword, from, to } = {}) =>
  http.get('/v1/admin/orders', { params: { page, size, status, keyword, from, to } }).then((res) => res.data);

export const changeOrderStatus = (id, newStatus, note) =>
  http.patch(`/v1/admin/orders/${id}/status`, { newStatus, note }).then((res) => res.data);
