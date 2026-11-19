import http from './http';

// Admin audit logs, /api/v1/admin/audit-logs; requires audit:view.
export const listAdminAuditLogs = ({ page = 0, size = 20 } = {}, config = {}) =>
  http.get('/v1/admin/audit-logs', { ...config, params: { page, size } }).then((res) => res.data);
