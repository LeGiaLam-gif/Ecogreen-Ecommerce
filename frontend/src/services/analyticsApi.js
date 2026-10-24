import http from './http';

// B09 admin reports, /api/v1/admin/reports/* (see docs/ai/contracts/B09-analytics.md). Every call needs analytics:view.
// Paged ones resolve to { items, meta }; the others to the object itself. Times are UTC ISO-8601.

// Products whose AVAILABLE quantity is <= threshold (default 10), lowest first: [{ productId, productName, sku, available }].
export const getLowStockReport = ({ threshold, page = 0, size = 20 } = {}) =>
  http.get('/v1/admin/reports/low-stock', { params: { threshold, page, size } }).then((res) => res.data);

// { status: 'PENDING_PAYMENT', count } - "pending" means waiting for payment.
export const getPendingOrdersCount = () =>
  http.get('/v1/admin/reports/pending-orders-count').then((res) => res.data);

// from / to: yyyy-MM-dd (both inclusive); default = this month up to today.
// -> { from, to, currency: 'VND', items: [{ status, count, totalAmount }], totalAmount }
export const getRefundSummary = ({ from, to } = {}) =>
  http.get('/v1/admin/reports/refund-summary', { params: { from, to } }).then((res) => res.data);

// Orders per customer, revenue statuses only, most orders first: [{ userId, orderCount, totalSpent, firstOrderAt, lastOrderAt }].
export const getCustomerFrequency = ({ page = 0, size = 20 } = {}) =>
  http.get('/v1/admin/reports/customer-frequency', { params: { page, size } }).then((res) => res.data);
