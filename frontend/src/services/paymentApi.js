import http from './http';

// /api/v1 endpoints (see docs/ai/contracts/B06-payment.md). The browser returning from the gateway is NOT proof of payment:
// only the server-side `status` of getPaymentForOrder is.

// { id, orderId, paymentMethod, amount, status, transactionId, provider, paidAt, mockEnabled }
export const getPaymentForOrder = (orderId) =>
  http.get(`/v1/payments/${orderId}`).then((res) => res.data);

// Starts an online payment (VNPAY). Resolves to { redirectUrl, provider }. Every call creates a new transaction reference.
// The server ignores a returnUrl that is not under its configured return URL.
export const initiatePayment = (orderId, returnUrl) =>
  http.post(`/v1/payments/${orderId}/initiate`, returnUrl ? { returnUrl } : {}).then((res) => res.data);

export const updatePaymentMethod = (orderId, method) =>
  http.put(`/v1/payments/${orderId}/method`, { method }).then((res) => res.data);

// TEMPORARY-COMPAT(B06): development only (the server answers 400 unless the mock provider is enabled). outcome: SUCCESS | FAILED.
export const mockPay = (orderId, outcome) =>
  http.post(`/v1/payments/${orderId}/mock-pay`, { outcome }).then((res) => res.data);

// Admin: payment + refund status of the orders of one page -> [{ orderId, paymentMethod, paymentStatus, amount, refundStatus }]
export const getPaymentSummaries = (orderIds) =>
  http.get('/v1/admin/payments', { params: { orderIds: orderIds.join(',') } }).then((res) => res.data);

// Admin: staff confirm that a VIETQR / MOMO transfer arrived (payment:confirm).
export const confirmPayment = (orderId, note) =>
  http.post(`/v1/admin/payments/${orderId}/confirm`, note ? { note } : {}).then((res) => res.data);

// Admin: staff confirm that a pending refund was sent (payment:refund).
export const completeRefund = (orderId, providerTransactionId) =>
  http.post(`/v1/admin/payments/${orderId}/refund/complete`,
    providerTransactionId ? { providerTransactionId } : {}).then((res) => res.data);
