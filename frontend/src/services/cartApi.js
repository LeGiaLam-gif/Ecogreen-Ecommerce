import http from './http';

// /api/v1 cart of the logged-in user (see docs/ai/contracts/B04-checkout.md). Every call resolves to the whole cart:
// { cartId, items[{ id, product, quantity, lineTotal, availableQuantity, warning }], subtotal, totalItems }.
export const getCart = () => http.get('/v1/cart').then((res) => res.data);

export const addToCart = (productId, quantity = 1) =>
  http.post('/v1/cart/items', { productId, quantity }).then((res) => res.data);

export const updateCartItem = (cartItemId, quantity) =>
  http.patch(`/v1/cart/items/${cartItemId}`, { quantity }).then((res) => res.data);

export const removeCartItem = (cartItemId) =>
  http.delete(`/v1/cart/items/${cartItemId}`).then((res) => res.data);

// 204, no body.
export const clearCart = () => http.delete('/v1/cart');
