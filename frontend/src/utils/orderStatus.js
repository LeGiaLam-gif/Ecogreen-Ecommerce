// Display metadata for the B05 order statuses. The status VALUES come from the backend (OrderStatus enum);
// this file only decides how they look. The browser never decides which transition is allowed: it renders the
// `allowedNextStatuses` / `canCancel` the server returns.
export const ORDER_STATUS = {
  PENDING_PAYMENT: { label: 'Chờ thanh toán', icon: '⏳', color: '#b45309', bg: '#fef3c7' },
  PAID: { label: 'Đã thanh toán', icon: '✅', color: '#15803d', bg: '#dcfce7' },
  PROCESSING: { label: 'Đang xử lý', icon: '📦', color: '#0369a1', bg: '#e0f2fe' },
  PACKED: { label: 'Đã đóng gói', icon: '🌿', color: '#0f766e', bg: '#ccfbf1' },
  SHIPPED: { label: 'Đang giao hàng', icon: '🚚', color: '#6d28d9', bg: '#ede9fe' },
  DELIVERED: { label: 'Đã giao hàng', icon: '🎉', color: '#166534', bg: '#bbf7d0' },
  CANCELLED: { label: 'Đã hủy', icon: '❌', color: '#b91c1c', bg: '#fee2e2' },
  RETURN_REQUESTED: { label: 'Yêu cầu trả hàng', icon: '♻️', color: '#a16207', bg: '#fef9c3' },
  RETURNED: { label: 'Đã trả hàng', icon: '↩️', color: '#475569', bg: '#e2e8f0' },
  REFUNDED: { label: 'Đã hoàn tiền', icon: '💸', color: '#334155', bg: '#e2e8f0' },
};

export const ORDER_STATUS_KEYS = Object.keys(ORDER_STATUS);

export const orderStatusInfo = (status) =>
  ORDER_STATUS[status] || { label: status, icon: '', color: '#333', bg: '#f1f5f9' };
