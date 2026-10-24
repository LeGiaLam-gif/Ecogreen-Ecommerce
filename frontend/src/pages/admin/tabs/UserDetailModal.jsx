import React, { useEffect, useState } from 'react';
import { getAdminUser, getAdminUserOrders } from '../../../services/userApi';
import { orderStatusInfo } from '../../../utils/orderStatus';
import AdminPagination from '../AdminPagination';

const ORDER_PAGE_SIZE = 10;
const formatDate = (v) => (v ? new Date(v).toLocaleString('vi-VN') : '—');

/**
 * Customer detail (B08): account data, the two counters and the order history, paged by the server. Needs user:view.
 * `onToggle` (lock / unlock) is passed only to users who hold user:disable and only for accounts other than their own.
 */
const UserDetailModal = ({ userId, onClose, onToggle }) => {
  const [user, setUser] = useState(null);
  const [orders, setOrders] = useState(null);
  const [orderPage, setOrderPage] = useState(0);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getAdminUser(userId)
      .then((u) => { if (!cancelled) { setUser(u); setError(''); } })
      .catch((e) => { if (!cancelled) setError(e.friendlyMessage || 'Không thể tải thông tin người dùng.'); });
    return () => { cancelled = true; };
  }, [userId]);

  useEffect(() => {
    let cancelled = false;
    getAdminUserOrders(userId, { page: orderPage, size: ORDER_PAGE_SIZE })
      .then((o) => { if (!cancelled) setOrders(o); })
      .catch((e) => { if (!cancelled) setError(e.friendlyMessage || 'Không thể tải lịch sử đơn hàng.'); });
    return () => { cancelled = true; };
  }, [userId, orderPage]);

  return (
    <div className="admin-modal-overlay">
      <div className="admin-modal-card" style={{ maxWidth: '820px' }}>
        <h3 style={{ color: '#1b3a1f', marginTop: 0 }}>👤 Chi tiết tài khoản #{userId}</h3>
        {error && <p className="admin-error-text">{error}</p>}
        {!user && !error && <div className="admin-empty">Đang tải...</div>}
        {user && (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 20px', marginBottom: '14px' }}>
              <div><span className="admin-cell-muted">Tên đăng nhập: </span><strong>{user.username}</strong></div>
              <div><span className="admin-cell-muted">Email: </span>{user.email}</div>
              <div><span className="admin-cell-muted">Vai trò: </span>{(user.roles || []).join(', ') || '—'}</div>
              <div>
                <span className="admin-cell-muted">Trạng thái: </span>
                <strong style={{ color: user.active ? '#2e7d32' : '#9ca3af' }}>{user.active ? 'Hoạt động' : 'Đã khóa'}</strong>
              </div>
              <div><span className="admin-cell-muted">Ngày tạo: </span>{formatDate(user.createdAt)}</div>
              <div>
                <span className="admin-cell-muted">Số đơn / yêu cầu đổi trả: </span>
                <strong>{user.orderCount ?? 0}</strong> / <strong>{user.returnRequestCount ?? 0}</strong>
              </div>
            </div>
            {onToggle && (
              <div style={{ marginBottom: '14px' }}>
                <button type="button" onClick={() => onToggle(user)} className="admin-btn-action-edit">
                  {user.active ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
                </button>
              </div>
            )}
          </>
        )}

        <h4 style={{ margin: '8px 0' }}>Lịch sử đơn hàng</h4>
        {orders && (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Mã ĐH</th>
                  <th>Ngày đặt</th>
                  <th>Tổng tiền</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {orders.items.map((o) => {
                  const info = orderStatusInfo(o.status);
                  return (
                    <tr key={o.id}>
                      <td className="admin-cell-strong">#{o.id}</td>
                      <td className="admin-cell-muted">{formatDate(o.createdAt)}</td>
                      <td className="admin-cell-price">{Number(o.totalPrice).toLocaleString('vi-VN')} ₫</td>
                      <td>
                        <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '0.8rem', fontWeight: 600, backgroundColor: info.bg, color: info.color }}>
                          {info.label}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {orders.items.length === 0 && <div className="admin-empty">Người dùng này chưa có đơn hàng nào.</div>}
            <AdminPagination meta={orders.meta} page={orderPage} onPage={setOrderPage} noun="đơn" />
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '12px' }}>
          <button type="button" onClick={onClose} className="admin-btn-secondary">Đóng</button>
        </div>
      </div>
    </div>
  );
};

export default UserDetailModal;
