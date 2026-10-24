import React, { useEffect, useState } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { listAdminReturns, updateReturnStatus } from '../../../services/returnApi';
import { completeRefund } from '../../../services/paymentApi';
import { PERM } from '../../../utils/permissions';
import ImageGallery from '../ImageGallery';
import { RETURN_STATUS_KEYS, returnStatusInfo, returnActionLabel, returnNeedsNote } from '../../../utils/returnStatus';

const RETURN_PAGE_SIZE = 20;

// Status of the refund (B06) linked to a return.
const REFUND_LABELS = { PENDING: 'Chờ hoàn tiền', SUCCESS: 'Đã hoàn tiền', FAILED: 'Hoàn tiền thất bại' };

/** "Đổi / Trả hàng" tab. `initialStatus` comes from the dashboard's pending-returns card. */
const ReturnsTab = ({ initialStatus = 'ALL', onBadges }) => {
  const { hasPermission } = useAuth();
  const [errorMsg, setErrorMsg] = useState('');
  const [returns, setReturns] = useState([]);
  const [returnMeta, setReturnMeta] = useState({ page: 0, totalPages: 1, totalElements: 0 });
  const [returnPage, setReturnPage] = useState(0);
  const [returnStatusFilter, setReturnStatusFilter] = useState(initialStatus);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setErrorMsg('');
      try {
        const [r, pending] = await Promise.all([
          listAdminReturns({
            page: returnPage,
            size: RETURN_PAGE_SIZE,
            status: returnStatusFilter === 'ALL' ? undefined : returnStatusFilter,
          }),
          listAdminReturns({ page: 0, size: 1, status: 'PENDING' }).catch(() => null),
        ]);
        if (cancelled) return;
        setReturns(r.items);
        setReturnMeta(r.meta);
        if (onBadges) onBadges({ pendingReturns: pending?.meta?.totalElements ?? 0 });
      } catch (error) {
        if (!cancelled) setErrorMsg(error.friendlyMessage || 'Không thể tải dữ liệu.');
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [returnPage, returnStatusFilter, reloadKey]);

  // The buttons come from the server's `allowedNextStatuses`; the server re-checks the transition and the permission.
  const handleUpdateReturnStatus = async (ret, newStatus) => {
    let adminNote = null;
    if (returnNeedsNote(newStatus)) {
      const input = prompt(
        newStatus === 'REJECTED'
          ? 'Nhập lý do từ chối (bắt buộc, khách hàng sẽ thấy):'
          : 'Nhập thông tin cần khách bổ sung (bắt buộc, khách hàng sẽ thấy):',
        newStatus === 'REJECTED' ? 'Yêu cầu không đáp ứng điều kiện đổi trả trong 7 ngày.' : ''
      );
      if (input === null) return;
      if (input.trim() === '') {
        alert('Vui lòng nhập ghi chú phản hồi khách hàng.');
        return;
      }
      adminNote = input.trim();
    } else if (!window.confirm(`Xác nhận chuyển yêu cầu #${ret.id} sang "${returnStatusInfo(newStatus).label}"?`)) {
      return;
    }
    try {
      await updateReturnStatus(ret.id, newStatus, adminNote);
      setReloadKey((n) => n + 1);
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể cập nhật trạng thái đổi/trả.');
    }
  };

  // B06: staff confirm that the pending refund of this return's order was sent (payment:refund). Money never moves from here.
  const handleCompleteRefund = async (ret) => {
    const reference = prompt(`Mã giao dịch hoàn tiền cho đơn hàng #${ret.orderId} (có thể để trống):`, '');
    if (reference === null) return;
    try {
      await completeRefund(ret.orderId, reference.trim());
      setReloadKey((n) => n + 1);
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể hoàn tất hoàn tiền.');
    }
  };

  return (
    <>
      {errorMsg && <p className="admin-error-text">{errorMsg}</p>}
      <div className="admin-panel">
        {/* Lọc trạng thái đổi trả (lọc bởi máy chủ) */}
        <div className="admin-order-status-tabs">
          {['ALL', ...RETURN_STATUS_KEYS].map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => { setReturnPage(0); setReturnStatusFilter(key); }}
              className={`admin-status-pill ${returnStatusFilter === key ? 'active' : ''}`}
            >
              {key === 'ALL' ? 'Tất cả yêu cầu' : returnStatusInfo(key).label}
            </button>
          ))}
        </div>

        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Mã YC</th>
                <th>Mã Đơn</th>
                <th>Khách hàng</th>
                <th>Lý do &amp; mô tả</th>
                <th>Sản phẩm trả</th>
                <th>Ảnh</th>
                <th>Trạng thái</th>
                <th>Ghi chú phản hồi</th>
                <th style={{ textAlign: 'center' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {returns.map((r) => {
                const info = returnStatusInfo(r.status);
                const next = hasPermission(PERM.RETURN_PROCESS) ? (r.allowedNextStatuses || []) : []; // UX only
                return (
                  <tr key={r.id}>
                    <td className="admin-cell-strong">#{r.id}</td>
                    <td className="admin-cell-strong">#{r.orderId}</td>
                    <td>{r.username}</td>
                    <td style={{ maxWidth: '240px', fontSize: '0.88rem', color: '#4b5563' }}>
                      <div style={{ color: '#e65100', fontWeight: 600 }}>{r.reason}</div>
                      {r.description || '—'}
                    </td>
                    <td style={{ fontSize: '0.85rem' }}>
                      {(r.items || []).length === 0
                        ? <span className="admin-cell-muted">Toàn bộ đơn (yêu cầu cũ)</span>
                        : r.items.map((it) => (
                          <div key={it.orderItemId}>{it.productName} × {it.quantity}</div>
                        ))}
                    </td>
                    <td>
                      <ImageGallery urls={r.images || []} />
                    </td>
                    <td>
                      <span style={{
                        display: 'inline-block',
                        padding: '4px 10px',
                        borderRadius: '12px',
                        fontSize: '0.78rem',
                        fontWeight: 'bold',
                        backgroundColor: info.bg,
                        color: info.color,
                      }}>
                        {info.label}
                      </span>
                      {r.refund && (
                        <div style={{ fontSize: '0.78rem', color: '#555', marginTop: '4px' }}>
                          Hoàn tiền: {Number(r.refund.amount).toLocaleString('vi-VN')} ₫ — {REFUND_LABELS[r.refund.status] || r.refund.status}
                        </div>
                      )}
                      {r.refundMessage && (
                        <div style={{ fontSize: '0.78rem', color: '#555', marginTop: '4px' }}>{r.refundMessage}</div>
                      )}
                    </td>
                    <td style={{ fontSize: '0.88rem', color: '#555' }}>{r.adminNote || '—'}</td>
                    <td style={{ textAlign: 'center' }}>
                      {next.length > 0 ? (
                        <div style={{ display: 'flex', gap: '8px', justifyContent: 'center', flexWrap: 'wrap' }}>
                          {next.map((st) => (
                            <button
                              key={st}
                              onClick={() => handleUpdateReturnStatus(r, st)}
                              className={st === 'REJECTED' ? 'admin-btn-reject' : 'admin-btn-approve'}
                            >
                              {returnActionLabel(st)}
                            </button>
                          ))}
                        </div>
                      ) : (
                        <span style={{ color: '#9ca3af', fontSize: '0.85rem' }}>
                          {r.status === 'REFUNDED' ? 'Đã hoàn tất' : '—'}
                        </span>
                      )}
                      {r.refund && r.refund.status === 'PENDING' && hasPermission(PERM.PAYMENT_REFUND) && (
                        <div style={{ marginTop: '6px' }}>
                          <button type="button" onClick={() => handleCompleteRefund(r)} className="admin-btn-approve">
                            Hoàn tất hoàn tiền
                          </button>
                          <div style={{ fontSize: '0.75rem', color: '#555', marginTop: '4px' }}>
                            Bấm sau khi đã chuyển tiền cho khách.
                          </div>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {returns.length === 0 && (
            <div className="admin-empty">Không có yêu cầu đổi/trả nào phù hợp.</div>
          )}
          {returnMeta.totalPages > 1 && (
            <div className="admin-pagination">
              <button type="button" disabled={returnPage === 0} onClick={() => setReturnPage((x) => x - 1)}>← Trước</button>
              <span>Trang {returnPage + 1} / {returnMeta.totalPages} ({returnMeta.totalElements} yêu cầu)</span>
              <button type="button" disabled={returnPage + 1 >= returnMeta.totalPages} onClick={() => setReturnPage((x) => x + 1)}>Sau →</button>
            </div>
          )}
        </div>
      </div>
    </>
  );
};

export default ReturnsTab;
