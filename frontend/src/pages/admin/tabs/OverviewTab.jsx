import React, { useEffect, useState } from 'react';
import { listAdminOrders, changeOrderStatus } from '../../../services/orderApi';
import { listAdminReturns } from '../../../services/returnApi';
import {
  getPendingOrdersCount, getLowStockReport, getRefundSummary, getCustomerFrequency,
} from '../../../services/analyticsApi';
import { ORDER_STATUS, orderStatusInfo } from '../../../utils/orderStatus';
import { useAuth } from '../../../context/AuthContext';
import { PERM } from '../../../utils/permissions';
import SalesDashboard from '../SalesDashboard';

const TOP_CUSTOMERS = 10;
const formatVnd = (v) => `${Number(v || 0).toLocaleString('vi-VN')} ₫`;
const formatDate = (v) => (v ? new Date(v).toLocaleDateString('vi-VN') : '—');

// One source failing (or not allowed) must not blank the whole dashboard: each request resolves to { value } or { error }.
const settle = (promise) => promise.then((value) => ({ value }), (error) => ({ error }));

/** A clickable summary card. `tone` picks the colour class of the existing CSS (alert-warning / -danger / -info / -success). */
const AlertCard = ({ icon, tone, num, label, desc, title, onClick }) => (
  <div className={`admin-alert-card alert-${tone}`} onClick={onClick} title={title}>
    <div className="alert-card-icon">{icon}</div>
    <div className="alert-card-content">
      <div className="alert-card-num">{num}</div>
      <div className="alert-card-label">{label}</div>
      <div className="alert-card-desc">{desc}</div>
    </div>
    <div className="alert-card-arrow">→</div>
  </div>
);

/**
 * "Tổng quan" tab: action cards, reports and the newest orders. Every block is shown only to a user who holds the
 * permission of its API (the server checks it again). `onNavigate(tab, params)` switches tab (the shell owns the tab
 * state); `onBadges` reports the sidebar counters this tab just learnt.
 */
const OverviewTab = ({ onNavigate, onBadges }) => {
  const { user, isAdmin, hasPermission } = useAuth();
  const canOrders = hasPermission(PERM.ORDER_VIEW_ALL);
  const canReturns = hasPermission(PERM.RETURN_VIEW);
  const canAnalytics = hasPermission(PERM.ANALYTICS_VIEW);
  const canSetOrderStatus = hasPermission(PERM.ORDER_UPDATE);

  const [orders, setOrders] = useState([]);
  const [orderMeta, setOrderMeta] = useState({ page: 0, totalPages: 1, totalElements: 0 });
  const [pendingOrders, setPendingOrders] = useState(null); // B09: status PENDING_PAYMENT
  const [pendingReturns, setPendingReturns] = useState(null);
  const [lowStock, setLowStock] = useState(null); // B09: products with available <= 10
  const [refunds, setRefunds] = useState(null); // B09: this month
  const [customers, setCustomers] = useState(null); // B09: orders per customer
  const [errors, setErrors] = useState([]);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const skipped = Promise.resolve({ value: null });
      const [o, pendingO, pendingR, low, refund, freq] = await Promise.all([
        canOrders ? settle(listAdminOrders({ page: 0, size: 5 })) : skipped,
        canAnalytics ? settle(getPendingOrdersCount()) : skipped,
        canReturns ? settle(listAdminReturns({ page: 0, size: 1, status: 'PENDING' })) : skipped,
        canAnalytics ? settle(getLowStockReport({ page: 0, size: 1 })) : skipped,
        canAnalytics ? settle(getRefundSummary()) : skipped,
        canAnalytics ? settle(getCustomerFrequency({ page: 0, size: TOP_CUSTOMERS })) : skipped,
      ]);
      if (cancelled) return;
      const failed = [o, pendingO, pendingR, low, refund, freq].filter((r) => r.error).map((r) => r.error.friendlyMessage || 'Không thể tải một phần dữ liệu.');
      setErrors([...new Set(failed)]);
      if (o.value) {
        setOrders(o.value.items);
        setOrderMeta(o.value.meta);
      }
      setPendingOrders(pendingO.value ? pendingO.value.count : null);
      setPendingReturns(pendingR.value ? pendingR.value.meta.totalElements : null);
      setLowStock(low.value ? low.value.meta.totalElements : null);
      setRefunds(refund.value || null);
      setCustomers(freq.value ? freq.value.items : null);
      const badges = {};
      if (pendingO.value) badges.pendingOrders = pendingO.value.count;
      if (pendingR.value) badges.pendingReturns = pendingR.value.meta.totalElements;
      if (Object.keys(badges).length > 0) onBadges(badges);
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadKey]);

  const handleUpdateStatus = async (orderId, newStatus) => {
    try {
      await changeOrderStatus(orderId, newStatus);
      setReloadKey((n) => n + 1);
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể cập nhật trạng thái đơn hàng.');
    }
  };

  const refundByStatus = Object.fromEntries((refunds?.items || []).map((i) => [i.status, i]));

  return (
    <>
      {errors.map((message) => <p key={message} className="admin-error-text">{message}</p>)}
      <div className="admin-dashboard-view">
        {/* Welcome Card & Real-time Action Center */}
        <div className="admin-action-center">
          <div className="admin-action-header">
            <div>
              <h3 style={{ margin: 0, fontSize: '1.2rem', color: '#1b3a1f' }}>
                👋 Xin chào, {user?.username || 'Quản trị viên'}!
              </h3>
              <p style={{ margin: '4px 0 0', fontSize: '0.88rem', color: '#64748b' }}>
                Dưới đây là tình hình hoạt động kinh doanh và các tác vụ cần chú ý hôm nay.
              </p>
            </div>
            <div className="admin-date-badge">
              📅 {new Date().toLocaleDateString('vi-VN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
            </div>
          </div>

          {/* Action Alert Cards */}
          <div className="admin-alert-cards-grid">
            {pendingOrders !== null && (
              <AlertCard
                icon="⏳" tone={pendingOrders > 0 ? 'warning' : 'success'} num={pendingOrders}
                label="Đơn hàng chờ thanh toán"
                desc={pendingOrders > 0 ? 'Đã đặt, đang chờ khách thanh toán' : 'Không có đơn chờ thanh toán'}
                title="Bấm để xem danh sách đơn hàng chờ thanh toán"
                onClick={() => canOrders && onNavigate('orders', { orderStatus: 'PENDING_PAYMENT' })}
              />
            )}
            {pendingReturns !== null && (
              <AlertCard
                icon="♻️" tone={pendingReturns > 0 ? 'danger' : 'success'} num={pendingReturns}
                label="Đổi / Trả chờ duyệt"
                desc={pendingReturns > 0 ? 'Cần phản hồi khách trong 7 ngày' : 'Đã xử lý tất cả yêu cầu'}
                title="Bấm để xem yêu cầu đổi/trả cần xử lý"
                onClick={() => onNavigate('returns', { returnStatus: 'PENDING' })}
              />
            )}
            {lowStock !== null && (
              <AlertCard
                icon="⚠️" tone={lowStock > 0 ? 'info' : 'success'} num={lowStock}
                label="Sản phẩm sắp hết hàng"
                desc={lowStock > 0 ? 'Có thể bán ≤ 10 chiếc, cần nhập thêm' : 'Tồn kho ổn định'}
                title="Bấm để xem tồn kho sắp hết"
                onClick={() => hasPermission(PERM.INVENTORY_VIEW) && onNavigate('inventory', { lowStock: true })}
              />
            )}
            {refunds && (
              <AlertCard
                icon="💸" tone={(refundByStatus.PENDING?.count || 0) > 0 ? 'warning' : 'success'}
                num={formatVnd(refunds.totalAmount)}
                label="Hoàn tiền trong tháng"
                desc={`Chờ hoàn: ${formatVnd(refundByStatus.PENDING?.totalAmount)} (${refundByStatus.PENDING?.count || 0}) · Đã hoàn: ${formatVnd(refundByStatus.SUCCESS?.totalAmount)} (${refundByStatus.SUCCESS?.count || 0})`}
                title={`Các yêu cầu hoàn tiền tạo từ ${formatDate(refunds.from)} đến ${formatDate(refunds.to)}`}
                onClick={() => canReturns && onNavigate('returns', {})}
              />
            )}
          </div>
        </div>

        {/* Báo cáo doanh số: the revenue reports are still the legacy ADMIN-only endpoints (StatsController) */}
        <div style={{ marginTop: '24px' }}>
          {isAdmin
            ? <SalesDashboard />
            : <p className="admin-cell-muted">Biểu đồ doanh thu hiện chỉ dành cho tài khoản ADMIN.</p>}
        </div>

        {/* Tần suất mua theo khách (B09): no names, the report carries ids only */}
        {customers !== null && (
          <div className="admin-panel" style={{ marginTop: '24px' }}>
            <div className="admin-panel-toolbar">
              <div>
                <h4 style={{ margin: 0, color: '#1b3a1f', fontSize: '1.05rem' }}>🔁 Khách hàng mua nhiều nhất</h4>
                <small style={{ color: '#64748b' }}>Số đơn đã tính doanh thu theo từng khách ({TOP_CUSTOMERS} khách đầu bảng)</small>
              </div>
            </div>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Khách hàng</th>
                    <th>Số đơn</th>
                    <th>Tổng chi tiêu</th>
                    <th>Đơn đầu tiên</th>
                    <th>Đơn gần nhất</th>
                    {hasPermission(PERM.USER_VIEW) && <th style={{ textAlign: 'center' }}>Thao tác</th>}
                  </tr>
                </thead>
                <tbody>
                  {customers.map((c) => (
                    <tr key={c.userId}>
                      <td className="admin-cell-strong">Khách #{c.userId}</td>
                      <td>{c.orderCount}</td>
                      <td className="admin-cell-price">{formatVnd(c.totalSpent)}</td>
                      <td className="admin-cell-muted">{formatDate(c.firstOrderAt)}</td>
                      <td className="admin-cell-muted">{formatDate(c.lastOrderAt)}</td>
                      {hasPermission(PERM.USER_VIEW) && (
                        <td style={{ textAlign: 'center' }}>
                          <button onClick={() => onNavigate('users', { userId: c.userId })} className="admin-btn-action-edit">
                            Xem hồ sơ
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
              {customers.length === 0 && <div className="admin-empty">Chưa có khách hàng nào có đơn hàng.</div>}
            </div>
          </div>
        )}

        {/* Đơn hàng mới nhất */}
        {canOrders && (
          <div className="admin-panel" style={{ marginTop: '24px' }}>
            <div className="admin-panel-toolbar">
              <div>
                <h4 style={{ margin: 0, color: '#1b3a1f', fontSize: '1.05rem' }}>⏱️ Đơn hàng mới nhất</h4>
                <small style={{ color: '#64748b' }}>5 đơn hàng vừa được khách hàng đặt gần đây</small>
              </div>
              <button
                onClick={() => onNavigate('orders', {})}
                className="admin-link-btn"
                style={{ fontWeight: 600, color: '#2e7d32' }}
              >
                Xem tất cả đơn hàng ({orderMeta.totalElements}) →
              </button>
            </div>

            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Mã ĐH</th>
                    <th>Khách hàng</th>
                    <th>Thời gian</th>
                    <th>Tổng tiền</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'center' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.map((o) => {
                    const sInfo = orderStatusInfo(o.status);
                    // Changing a status needs order:update, moving to CANCELLED also order:cancel (OrderV1Controller).
                    const next = canSetOrderStatus
                      ? (o.allowedNextStatuses || []).filter((st) => st !== 'CANCELLED' || hasPermission(PERM.ORDER_CANCEL))
                      : [];
                    return (
                      <tr key={o.id}>
                        <td className="admin-cell-strong">#{o.id}</td>
                        <td>{o.customerName || 'Khách vãng lai'}</td>
                        <td className="admin-cell-muted">{new Date(o.createdAt).toLocaleString('vi-VN')}</td>
                        <td className="admin-cell-price">{Number(o.totalPrice).toLocaleString('vi-VN')} ₫</td>
                        <td>
                          <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '0.8rem', fontWeight: 600, backgroundColor: sInfo.bg, color: sInfo.color }}>
                            {sInfo.label}
                          </span>
                        </td>
                        <td style={{ textAlign: 'center' }}>
                          {next.length === 0 ? (
                            <span className="admin-cell-muted">—</span>
                          ) : (
                            <select
                              value=""
                              onChange={(e) => e.target.value && handleUpdateStatus(o.id, e.target.value)}
                              className="admin-status-select"
                            >
                              <option value="">Chuyển sang…</option>
                              {next.map((st) => (
                                <option key={st} value={st}>{ORDER_STATUS[st]?.label || st}</option>
                              ))}
                            </select>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {orders.length === 0 && <div className="admin-empty">Chưa có đơn hàng nào phát sinh.</div>}
            </div>
          </div>
        )}
      </div>
    </>
  );
};

export default OverviewTab;
