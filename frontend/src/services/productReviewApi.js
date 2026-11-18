import http from './http';

export const getProductReviews = (productId) => http.get(`/v1/products/${productId}/reviews`).then((res) => res.data);
export const submitProductReview = (productId, payload) =>
  http.post(`/v1/products/${productId}/reviews`, payload).then((res) => res.data);
