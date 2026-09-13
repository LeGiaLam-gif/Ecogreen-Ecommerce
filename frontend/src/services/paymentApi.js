import http from './http';

export const getPaymentForOrder = (orderId) =>
  http.get(`/payments/order/${orderId}`).then((res) => res.data);

export const payNow = (orderId) =>
  http.post(`/payments/order/${orderId}/pay`).then((res) => res.data);
