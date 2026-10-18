import http from './http';

// B04 checkout (see docs/ai/contracts/B04-checkout.md). The body carries only the address (addressId OR customerName /
// customerPhone / shippingAddress), an optional discountCode and the paymentMethod - never a price or a total.
// Resolves to the created order (same shape as getOrderById).
export const checkout = (payload) => http.post('/v1/checkout', payload).then((res) => res.data);

// The server's numbers for the cart: { items[], subtotal, discountAmount, shippingFee, total, issues[] }. Writes nothing.
// `signal` (an AbortSignal) lets the caller drop a stale request.
export const previewCheckout = (discountCode, signal) =>
  http.post('/v1/checkout/preview', discountCode ? { discountCode } : {}, { signal }).then((res) => res.data);

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
