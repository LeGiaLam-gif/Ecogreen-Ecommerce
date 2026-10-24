import http from './http';

// B10 notification log for staff, /api/v1/admin/notifications (see docs/ai/contracts/B10-notification.md); needs analytics:view.
// status: PENDING | SENT | FAILED (anything else is a 400). sort: createdAt | id | status | type (default newest first).
// Resolves to { items, meta }; item: { id, userId, type, channel, title, status, failureReason, sentAt, createdAt }.
// The server returns no message body and no email address.
export const listAdminNotifications = ({ status, page = 0, size = 20, sort } = {}, config = {}) =>
  http.get('/v1/admin/notifications', { ...config, params: { status, page, size, sort } }).then((res) => res.data);
