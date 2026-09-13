import http from './http';

export const getCart = () => http.get('/cart').then((res) => res.data);

export const addToCart = (productId, quantity = 1) =>
  http.post('/cart/items', { productId, quantity }).then((res) => res.data);

export const updateCartItem = (cartItemId, quantity) =>
  http.put(`/cart/items/${cartItemId}`, { quantity }).then((res) => res.data);

export const removeCartItem = (cartItemId) =>
  http.delete(`/cart/items/${cartItemId}`).then((res) => res.data);

export const clearCart = () => http.delete('/cart');
