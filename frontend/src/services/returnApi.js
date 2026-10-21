import http from './http';

// B07 returns, /api/v1 (see docs/ai/contracts/B07-return.md). Lists resolve to { items, meta }.

// `form` carries only what the customer chose: orderId, reason, description, items (array of { orderItemId, quantity })
// and 1-5 image Files. The server decides everything else (owner, status, prices, refund).
// The Content-Type is NOT set by hand: the browser adds the multipart boundary for a FormData body.
export const createReturn = ({ orderId, reason, description, items, images }) => {
  const body = new FormData();
  body.append('orderId', String(orderId));
  body.append('reason', reason);
  if (description) body.append('description', description);
  body.append('items', JSON.stringify(items.map((i) => ({ orderItemId: i.orderItemId, quantity: i.quantity }))));
  (images || []).forEach((file) => body.append('images', file));
  return http.post('/v1/returns', body).then((res) => res.data);
};

export const getMyReturns = ({ page = 0, size = 20 } = {}) =>
  http.get('/v1/returns/my', { params: { page, size } }).then((res) => res.data);

export const getReturn = (id) => http.get(`/v1/returns/${id}`).then((res) => res.data);

export const listAdminReturns = ({ page = 0, size = 20, status } = {}) =>
  http.get('/v1/admin/returns', { params: { page, size, status } }).then((res) => res.data);

export const updateReturnStatus = (id, newStatus, adminNote) =>
  http.patch(`/v1/admin/returns/${id}/status`, { newStatus, adminNote }).then((res) => res.data);
