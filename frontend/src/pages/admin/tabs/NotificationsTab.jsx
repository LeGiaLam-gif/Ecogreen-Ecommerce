import React, { useEffect, useState } from 'react';
import { listAdminNotifications } from '../../../services/notificationApi';
import AdminPagination from '../AdminPagination';

const PAGE_SIZE = 20;

const STATUS_INFO = {
  PENDING: { label: 'Đang chờ gửi', color: '#b45309', bg: '#fef3c7' },
  SENT: { label: 'Đã gửi', color: '#15803d', bg: '#dcfce7' },
  FAILED: { label: 'Gửi thất bại', color: '#b91c1c', bg: '#fee2e2' },
};

const TYPE_LABELS = {
  ORDER_CREATED: 'Đặt hàng thành công',
  PAYMENT_SUCCESS: 'Thanh toán thành công',
  ORDER_SHIPPED: 'Đang giao hàng',
  ORDER_DELIVERED: 'Đã giao hàng',
  ORDER_CANCELLED: 'Đơn bị hủy',
  RETURN_CREATED: 'Đã ghi nhận đổi/trả',
  RETURN_APPROVED: 'Đổi/trả được duyệt',
  RETURN_REJECTED: 'Đổi/trả bị từ chối',
  RETURN_INFO_REQUESTED: 'Cần bổ sung thông tin',
  RETURN_REFUNDED: 'Đã ghi nhận hoàn tiền',
};

// The short codes the server stores in failureReason (never an exception message).
const FAILURE_LABELS = {
  USER_DISABLED: 'Tài khoản đã bị khóa',
  NO_EMAIL: 'Tài khoản không có email',
  SEND_ERROR: 'Máy chủ mail từ chối hoặc lỗi',
  EMAIL_NOT_CONFIGURED: 'Chưa cấu hình SMTP',
};

const formatDate = (v) => (v ? new Date(v).toLocaleString('vi-VN') : '—');

/** "Thông báo" tab (B10): the delivery log, read only. Needs analytics:view. No message body or address is ever shown. */
const NotificationsTab = () => {
  const [items, setItems] = useState([]);
  const [meta, setMeta] = useState(null);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('ALL');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      setLoading(true);
      try {
        const result = await listAdminNotifications(
          { status: status === 'ALL' ? undefined : status, page, size: PAGE_SIZE },
          { signal: controller.signal },
        );
        if (controller.signal.aborted) return;
        setItems(result.items || []);
        setMeta(result.meta || null);
        setError('');
      } catch (e) {
        if (!controller.signal.aborted) setError(e.friendlyMessage || 'Không thể tải nhật ký thông báo.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    })();
    return () => controller.abort();
  }, [status, page]);

  return (
    <div className="admin-panel">
      <div className="admin-order-status-tabs">
        {['ALL', 'PENDING', 'SENT', 'FAILED'].map((key) => (
          <button
            key={key}
            type="button"
            onClick={() => { setPage(0); setStatus(key); }}
            className={`admin-status-pill ${status === key ? 'active' : ''}`}
          >
            {key === 'ALL' ? 'Tất cả' : STATUS_INFO[key].label}
          </button>
        ))}
      </div>

      {error && <p className="admin-error-text">{error}</p>}

      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Người nhận</th>
              <th>Loại</th>
              <th>Kênh</th>
              <th>Tiêu đề</th>
              <th>Trạng thái</th>
              <th>Lý do lỗi</th>
              <th>Gửi lúc</th>
              <th>Tạo lúc</th>
            </tr>
          </thead>
          <tbody>
            {items.map((n) => {
              const info = STATUS_INFO[n.status] || { label: n.status, color: '#333', bg: '#f1f5f9' };
              return (
                <tr key={n.id}>
                  <td className="admin-cell-strong">#{n.id}</td>
                  <td>Người dùng #{n.userId}</td>
                  <td title={n.type}>{TYPE_LABELS[n.type] || n.type}</td>
                  <td>{n.channel}</td>
                  <td>{n.title}</td>
                  <td>
                    <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '0.8rem', fontWeight: 600, backgroundColor: info.bg, color: info.color }}>
                      {info.label}
                    </span>
                  </td>
                  <td className="admin-cell-muted" title={n.failureReason || ''}>
                    {n.failureReason ? (FAILURE_LABELS[n.failureReason] || n.failureReason) : '—'}
                  </td>
                  <td className="admin-cell-muted">{formatDate(n.sentAt)}</td>
                  <td className="admin-cell-muted">{formatDate(n.createdAt)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {items.length === 0 && (
          <div className="admin-empty">{loading ? 'Đang tải...' : 'Chưa có thông báo nào trong trạng thái đã chọn.'}</div>
        )}
        <AdminPagination meta={meta} page={page} onPage={setPage} noun="thông báo" />
      </div>
    </div>
  );
};

export default NotificationsTab;
