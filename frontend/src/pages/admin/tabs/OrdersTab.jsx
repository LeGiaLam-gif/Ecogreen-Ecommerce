import React, { useEffect, useState } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { PERM } from '../../../utils/permissions';
import { listAdminOrders, changeOrderStatus } from '../../../services/orderApi';
import { getPaymentSummaries, confirmPayment, completeRefund } from '../../../services/paymentApi';
import { getOrdersByStatus } from '../../../services/reportApi';
import { ORDER_STATUS, ORDER_STATUS_KEYS, orderStatusInfo } from '../../../utils/orderStatus';

const ORDER_PAGE_SIZE = 20;

/** "Đơn hàng" tab. `initialStatus` comes from the dashboard's pending-orders card. */
const OrdersTab = ({ initialStatus = 'ALL', onBadges }) => {
  const { hasPermission, isAdmin } = useAuth();
  // The per-status counters come from the legacy /api/admin/reports/orders-by-status, which the server still limits to ADMIN
  // (StatsController.requireAdmin). Other staff get the list without counters instead of an error.
  const countsAvailable = isAdmin;
  const [errorMsg, setErrorMsg] = useState('');
  const [orders, setOrders] = useState([]);
  // B06: payment + refund status of the orders on the page, by order id (admin API, payment:view).
  const [paymentByOrder, setPaymentByOrder] = useState({});
  const [orderMeta, setOrderMeta] = useState({ page: 0, totalPages: 1, totalElements: 0 });
  const [orderPage, setOrderPage] = useState(0);
  const [orderCounts, setOrderCounts] = useState({});
  const [searchTerm, setSearchTerm] = useState('');
  const [orderStatusFilter, setOrderStatusFilter] = useState(initialStatus);
  const [orderKeyword, setOrderKeyword] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  // Order search is done by the server; wait for the admin to stop typing before asking again.
  useEffect(() => {
    const t = setTimeout(() => {
      setOrderPage(0);
      setOrderKeyword(searchTerm.trim());
    }, 350);
    return () => clearTimeout(t);
  }, [searchTerm]);

  // The payment column is an extra: a failure here never blocks the order list.
  const loadPaymentSummaries = async (pageOrders) => {
    if (!hasPermission(PERM.PAYMENT_VIEW) || pageOrders.length === 0) {
      setPaymentByOrder({});
      return;
    }
    try {
      const rows = await getPaymentSummaries(pageOrders.map((o) => o.id));
      setPaymentByOrder(Object.fromEntries(rows.map((r) => [r.orderId, r])));
    } catch {
      setPaymentByOrder({});
    }
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setErrorMsg('');
      try {
        const [o, counts] = await Promise.all([
          listAdminOrders({
            page: orderPage,
            size: ORDER_PAGE_SIZE,
            status: orderStatusFilter === 'ALL' ? undefined : orderStatusFilter,
            keyword: orderKeyword || undefined,
          }),
          countsAvailable ? getOrdersByStatus() : Promise.resolve(null),
        ]);
        if (cancelled) return;
        setOrders(o.items);
        setOrderMeta(o.meta);
        if (counts) {
          setOrderCounts(counts);
          if (onBadges) onBadges({ pendingOrders: counts.PENDING_PAYMENT || 0 });
        }
        await loadPaymentSummaries(o.items);
      } catch (error) {
        if (!cancelled) setErrorMsg(error.friendlyMessage || 'Không thể tải dữ liệu.');
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderPage, orderStatusFilter, orderKeyword, reloadKey]);

  const reload = () => setReloadKey((n) => n + 1);

  const handleUpdateStatus = async (orderId, newStatus) => {
    try {
      await changeOrderStatus(orderId, newStatus);
      reload();
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể cập nhật trạng thái đơn hàng.');
    }
  };

  // --- Payments (B06) ---
  const handleConfirmPayment = async (orderId) => {
    if (!window.confirm(`Xác nhận đã nhận đủ tiền cho đơn hàng #${orderId}? Đơn hàng sẽ chuyển sang "Đã thanh toán".`)) return;
    try {
      await confirmPayment(orderId);
      reload();
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể xác nhận thanh toán.');
    }
  };

  const handleCompleteRefund = async (orderId) => {
    const reference = prompt(`Mã giao dịch hoàn tiền cho đơn hàng #${orderId} (có thể để trống):`, '');
    if (reference === null) return;
    try {
      await completeRefund(orderId, reference.trim());
      reload();
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể hoàn tất hoàn tiền.');
    }
  };

  return (
    <>
      {errorMsg && <p className="admin-error-text">{errorMsg}</p>}
      <div className="admin-panel">
        {/* Thanh lọc trạng thái nhanh dạng Pills */}
        <div className="admin-order-status-tabs">
          {[
            { key: 'ALL', label: 'Tất cả đơn', count: Object.values(orderCounts).reduce((a, b) => a + b, 0) },
            ...ORDER_STATUS_KEYS.map((k) => ({ key: k, label: ORDER_STATUS[k].label, count: orderCounts[k] || 0 })),
          ].map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => { setOrderPage(0); setOrderStatusFilter(item.key); }}
              className={`admin-status-pill ${orderStatusFilter === item.key ? 'active' : ''}`}
            >
              {item.label}{countsAvailable && <span className="pill-count"> ({item.count})</span>}
            </button>
          ))}
        </div>

        {/* Tìm kiếm */}
        <div className="admin-panel-toolbar">
          <input
            type="text"
            placeholder="Tìm theo mã đơn (#123) hoặc tên khách hàng..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="admin-search-input"
            style={{ width: '360px' }}
          />
          <div style={{ fontSize: '0.88rem', color: '#64748b' }}>
            Hiển thị <strong>{orders.length}</strong> / {orderMeta.totalElements} đơn hàng
          </div>
        </div>

        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Mã ĐH</th>
                <th>Khách hàng</th>
                <th>Ngày đặt</th>
                <th>Tổng tiền</th>
                <th>Trạng thái hiện tại</th>
                <th>Thanh toán</th>
                <th style={{ textAlign: 'center' }}>Cập nhật trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => {
                const sInfo = orderStatusInfo(o.status);
                // The server lists the legal next statuses; the UI only hides what the user may not do: changing a status
                // needs order:update, and moving to CANCELLED needs order:cancel as well (OrderV1Controller).
                const nextStatuses = hasPermission(PERM.ORDER_UPDATE)
                  ? (o.allowedNextStatuses || []).filter((st) => st !== 'CANCELLED' || hasPermission(PERM.ORDER_CANCEL))
                  : [];
                return (
                  <tr key={o.id}>
                    <td className="admin-cell-strong">#{o.id}</td>
                    <td>
                      <div style={{ fontWeight: 600 }}>{o.customerName || 'Khách hàng'}</div>
                      <small style={{ color: '#64748b' }}>{o.customerPhone || ''}</small>
                    </td>
                    <td className="admin-cell-muted">
                      {new Date(o.createdAt).toLocaleString('vi-VN')}
                    </td>
                    <td className="admin-cell-price">
                      {Number(o.totalPrice).toLocaleString('vi-VN')} ₫
                    </td>
                    <td>
                      <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '0.82rem', fontWeight: 600, backgroundColor: sInfo.bg, color: sInfo.color }}>
                        {sInfo.label}
                      </span>
                    </td>
                    <td>
                      {(() => {
                        const pay = paymentByOrder[o.id];
                        if (!pay) return <span className="admin-cell-muted">—</span>;
                        const canConfirm = (pay.paymentMethod === 'VIETQR' || pay.paymentMethod === 'MOMO')
                          && pay.paymentStatus === 'PENDING' && o.status === 'PENDING_PAYMENT'
                          && hasPermission(PERM.PAYMENT_CONFIRM); // UX only: the server checks the permission
                        const canCompleteRefund = pay.refundStatus === 'PENDING' && hasPermission(PERM.PAYMENT_REFUND);
                        return (
                          <div>
                            <div>{pay.paymentMethod} · {pay.paymentStatus}</div>
                            {pay.refundStatus && <small className="admin-cell-muted">Hoàn tiền: {pay.refundStatus}</small>}
                            {canConfirm && (
                              <div>
                                <button type="button" onClick={() => handleConfirmPayment(o.id)}>Xác nhận đã nhận tiền</button>
                              </div>
                            )}
                            {canCompleteRefund && (
                              <div>
                                <button type="button" onClick={() => handleCompleteRefund(o.id)}>Hoàn tất hoàn tiền</button>
                              </div>
                            )}
                          </div>
                        );
                      })()}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      {nextStatuses.length === 0 ? (
                        <span className="admin-cell-muted">—</span>
                      ) : (
                        <select
                          value=""
                          onChange={(e) => e.target.value && handleUpdateStatus(o.id, e.target.value)}
                          className="admin-status-select"
                        >
                          <option value="">Chuyển sang…</option>
                          {nextStatuses.map((st) => (
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
          {orders.length === 0 && (
            <div className="admin-empty">Không có đơn hàng nào trong trạng thái đã chọn.</div>
          )}
          {orderMeta.totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px', padding: '14px' }}>
              <button type="button" disabled={orderPage === 0} onClick={() => setOrderPage((x) => x - 1)}>← Trước</button>
              <span>Trang {orderPage + 1} / {orderMeta.totalPages}</span>
              <button type="button" disabled={orderPage + 1 >= orderMeta.totalPages} onClick={() => setOrderPage((x) => x + 1)}>Sau →</button>
            </div>
          )}
        </div>
      </div>
    </>
  );
};

export default OrdersTab;
