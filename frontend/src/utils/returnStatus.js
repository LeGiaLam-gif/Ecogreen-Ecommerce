// Display metadata for the B07 return statuses. The status VALUES come from the backend (ReturnStatus enum); this file
// only decides how they look. The browser never decides which transition is allowed: it renders the
// `allowedNextStatuses` the server returns.
export const RETURN_STATUS = {
  PENDING: { label: 'Chờ xử lý', icon: '⏳', color: '#f57f17', bg: '#fff8e1' },
  INFO_REQUESTED: { label: 'Cần bổ sung thông tin', icon: '💬', color: '#0369a1', bg: '#e0f2fe' },
  APPROVED: { label: 'Đã duyệt', icon: '✅', color: '#2e7d32', bg: '#e8f5e9' },
  REJECTED: { label: 'Đã từ chối', icon: '❌', color: '#c62828', bg: '#ffebee' },
  RETURNED: { label: 'Đã nhận hàng trả', icon: '↩️', color: '#475569', bg: '#e2e8f0' },
  REFUNDED: { label: 'Đã hoàn tiền', icon: '💸', color: '#334155', bg: '#dcfce7' },
};

export const RETURN_STATUS_KEYS = Object.keys(RETURN_STATUS);

export const returnStatusInfo = (status) =>
  RETURN_STATUS[status] || { label: status, icon: '', color: '#333', bg: '#f1f5f9' };

// Label of the button that moves a return to `status` (admin).
export const RETURN_ACTION_LABEL = {
  INFO_REQUESTED: 'Yêu cầu bổ sung',
  APPROVED: 'Duyệt',
  REJECTED: 'Từ chối',
  RETURNED: 'Đã nhận hàng',
  REFUNDED: 'Đánh dấu đã hoàn tiền',
};

export const returnActionLabel = (status) => RETURN_ACTION_LABEL[status] || status;

// UX hint only: the server enforces the note rule (REJECTED and INFO_REQUESTED need an admin note).
export const returnNeedsNote = (status) => status === 'REJECTED' || status === 'INFO_REQUESTED';

// Client-side image limits, UX only; the server re-checks the content, size and count.
export const RETURN_MAX_IMAGES = 5;
export const RETURN_MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const RETURN_MAX_TOTAL_BYTES = Math.floor(5.5 * 1024 * 1024); // multipart request limit is 6 MB
export const RETURN_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

// Statuses that keep a return open (mirror of ReturnStatus.openStatuses on the server; display only).
export const RETURN_OPEN_STATUSES = ['PENDING', 'INFO_REQUESTED', 'APPROVED'];
export const isReturnOpen = (status) => RETURN_OPEN_STATUSES.includes(status);

// UX only: the server refuses a return for any order that is not DELIVERED.
export const canRequestReturn = (orderStatus) => orderStatus === 'DELIVERED';
