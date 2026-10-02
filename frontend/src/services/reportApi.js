import http from './http';

export const getRevenueOverTime = (range = '30d', groupBy) =>
    http.get('/admin/reports/revenue-over-time', { params: { range, groupBy } }).then((res) => res.data);

export const getOrdersByStatus = () =>
    http.get('/admin/reports/orders-by-status').then((res) => res.data);

export const getTopProducts = (limit = 5, range = '30d') =>
    http.get('/admin/reports/top-products', { params: { limit, range } }).then((res) => res.data);

export const getReportSummary = (range = '30d') =>
    http.get('/admin/reports/summary', { params: { range } }).then((res) => res.data);
