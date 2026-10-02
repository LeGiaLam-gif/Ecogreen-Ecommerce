import http from './http';

export const checkout = (orderData) => http.post('/orders', orderData).then((res) => res.data);

export const getMyOrders = () => http.get('/orders/my').then((res) => res.data);

export const getAllOrders = () => http.get('/orders').then((res) => res.data);

export const getOrderById = (id) => http.get(`/orders/${id}`).then((res) => res.data);

export const updateOrderStatus = (id, status) =>
  http.put(`/orders/${id}/status`, { status }).then((res) => res.data);
