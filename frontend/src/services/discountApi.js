import http from './http';

// B04 discount codes, /api/v1/admin/discounts (see docs/ai/contracts/B04-checkout.md). Reading needs discount:view, every
// change needs discount:manage. The list resolves to { items, meta }; sort: createdAt | code | id (default newest first).
// Body: { code, percentOff? (1-100) | amountOff? (> 0) - exactly one, minOrderAmount?, validFrom?, validTo?, active }.
export const listDiscounts = ({ page = 0, size = 20, sort } = {}) =>
  http.get('/v1/admin/discounts', { params: { page, size, sort } }).then((res) => res.data);

export const createDiscount = (body) => http.post('/v1/admin/discounts', body).then((res) => res.data);

// PUT replaces every field of the discount.
export const updateDiscount = (id, body) => http.put(`/v1/admin/discounts/${id}`, body).then((res) => res.data);

export const deleteDiscount = (id) => http.delete(`/v1/admin/discounts/${id}`);
