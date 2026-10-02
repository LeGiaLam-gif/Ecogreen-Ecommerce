import http from './http';

export const createReturnRequest = (data) =>
  http.post('/returns', data).then((res) => res.data);

export const getMyReturns = () =>
  http.get('/returns/my').then((res) => res.data);

export const getAllReturns = () =>
  http.get('/returns').then((res) => res.data);

export const updateReturnStatus = (id, status, adminNote) =>
  http.put(`/returns/${id}/status`, { status, adminNote }).then((res) => res.data);
