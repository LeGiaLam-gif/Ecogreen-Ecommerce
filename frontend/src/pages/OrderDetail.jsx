import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getOrderById, cancelOrder } from '../services/orderApi';
import { orderStatusInfo } from '../utils/orderStatus';
import { createReturn, getMyReturns } from '../services/returnApi';
import {
  returnStatusInfo, isReturnOpen, canRequestReturn, RETURN_MAX_IMAGES, RETURN_MAX_IMAGE_BYTES,
  RETURN_MAX_TOTAL_BYTES, RETURN_IMAGE_TYPES,
} from '../utils/returnStatus';
import { resolveProductImage } from '../utils/imageResolver';
import './OrderDetail.css';

const fmtVND = (n) => Number(n).toLocaleString('vi-VN') + ' ₫';

const OrderDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Return request modal state
  const [showReturnModal, setShowReturnModal] = useState(false);
  const [existingReturn, setExistingReturn] = useState(null);
  const [returnReason, setReturnReason] = useState('Sản phẩm bị lỗi / vỡ khi vận chuyển');
  const [returnDesc, setReturnDesc] = useState('');
  const [returnQty, setReturnQty] = useState({}); // orderItemId -> quantity to return (0 = not returned)
  const [returnFiles, setReturnFiles] = useState([]);
  const [returnError, setReturnError] = useState('');
  const [submittingReturn, setSubmittingReturn] = useState(false);
  const [returnSuccess, setReturnSuccess] = useState('');
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      getOrderById(id),
      getMyReturns({ page: 0, size: 100 }).catch(() => ({ items: [] }))
    ])
      .then(([orderData, returnsData]) => {
        setOrder(orderData);
        // Newest first: the latest return of this order (an older REJECTED one does not block a new request).
        const matched = (returnsData.items || []).find((r) => String(r.orderId) === String(id));
        if (matched) setExistingReturn(matched);
      })
      .catch((err) => setError(err.friendlyMessage || 'Không thể tải thông tin đơn hàng.'))
      .finally(() => setLoading(false));
  }, [id]);

  const openReturnModal = () => {
    setReturnQty({});
    setReturnFiles([]);
    setReturnError('');
    setShowReturnModal(true);
  };

  // Client-side checks are UX only; the server validates type (by content), size and count again.
  const handlePickFiles = (e) => {
    const picked = Array.from(e.target.files || []);
    e.target.value = '';
    const next = [...returnFiles, ...picked];
    if (next.length > RETURN_MAX_IMAGES) {
      setReturnError(`Chỉ được đính kèm tối đa ${RETURN_MAX_IMAGES} ảnh.`);
      return;
    }
    if (next.some((f) => !RETURN_IMAGE_TYPES.includes(f.type))) {
      setReturnError('Chỉ chấp nhận ảnh JPG, PNG hoặc WebP.');
      return;
    }
    if (next.some((f) => f.size > RETURN_MAX_IMAGE_BYTES)) {
      setReturnError('Mỗi ảnh tối đa 5 MB.');
      return;
    }
    if (next.reduce((sum, f) => sum + f.size, 0) > RETURN_MAX_TOTAL_BYTES) {
      setReturnError('Tổng dung lượng ảnh vượt quá giới hạn 5,5 MB. Vui lòng chọn ảnh nhỏ hơn.');
      return;
    }
    setReturnError('');
    setReturnFiles(next);
  };

  const handleSubmitReturn = async (e) => {
    e.preventDefault();
    const items = (order?.items || [])
      .filter((it) => Number(returnQty[it.id]) > 0)
      .map((it) => ({ orderItemId: it.id, quantity: Number(returnQty[it.id]) }));
    if (items.length === 0) {
      setReturnError('Vui lòng chọn ít nhất một sản phẩm cần trả.');
      return;
    }
    if (returnFiles.length === 0) {
      setReturnError('Vui lòng đính kèm ít nhất một ảnh thực tế.');
      return;
    }
    setSubmittingReturn(true);
    setReturnError('');
    try {
      const res = await createReturn({
        orderId: id,
        reason: returnReason,
        description: returnDesc,
        items,
        images: returnFiles,
      });
      setExistingReturn(res);
      setOrder(await getOrderById(id)); // the server moved the order to RETURN_REQUESTED
      setShowReturnModal(false);
      setReturnDesc('');
      setReturnSuccess('Yêu cầu đổi/trả hàng đã được gửi thành công! Đội ngũ EcoGreen sẽ liên hệ bạn sớm.');
    } catch (err) {
      setReturnError(err.friendlyMessage || 'Không thể gửi yêu cầu đổi/trả. Vui lòng thử lại.');
    } finally {
      setSubmittingReturn(false);
    }
  };

  const handleCancel = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn hủy đơn hàng này?')) return;
    setCancelling(true);
    try {
      setOrder(await cancelOrder(id));
    } catch (err) {
      alert(err.friendlyMessage || 'Không thể hủy đơn hàng.');
    } finally {
      setCancelling(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px' }}>
        <div className="spinner" style={{ margin: '0 auto 16px' }} />
        <p style={{ color: '#6b7280' }}>Đang tải đơn hàng #{id}...</p>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="od-page" style={{ textAlign: 'center', padding: '60px 20px' }}>
        <p style={{ color: '#c62828', fontSize: '1.1rem', marginBottom: '20px' }}>{error || 'Không tìm thấy đơn hàng.'}</p>
        <button className="od-back-btn" onClick={() => navigate('/orders')}>← Quay lại danh sách đơn hàng</button>
      </div>
    );
  }

  // Calculate timeline steps
  const isCancelled = order.status === 'CANCELLED';
  // Steps 1..5 = placed, confirmed, packed, shipping, done. Statuses past DELIVERED (return flow) show all steps done.
  const STEP_BY_STATUS = { PENDING_PAYMENT: 1, PAID: 2, PROCESSING: 3, PACKED: 4, SHIPPED: 5 };
  const getStepClass = (stepIndex) => {
    if (isCancelled) return '';
    const current = STEP_BY_STATUS[order.status];
    if (current === undefined) return 'completed';
    if (stepIndex < current) return 'completed';
    return stepIndex === current ? 'active' : '';
  };
  const statusInfo = orderStatusInfo(order.status);
  const canReturn = canRequestReturn(order.status) && !(existingReturn && isReturnOpen(existingReturn.status));
  const returnInfo = existingReturn ? returnStatusInfo(existingReturn.status) : null;

  return (
    <div className="od-page">
      <button className="od-back-btn" onClick={() => navigate('/orders')}>
        ← Quay lại đơn hàng của tôi
      </button>

      {returnSuccess && (
        <div className="od-return-banner" style={{ marginBottom: '20px' }}>
          <span className="od-return-banner-icon">🌿</span>
          <div className="od-return-banner-content">
            <h4>Thành công</h4>
            <p>{returnSuccess}</p>
          </div>
        </div>
      )}

      {/* Existing Return Request Banner */}
      {existingReturn && (
        <div className={`od-return-banner ${existingReturn.status}`}>
          <span className="od-return-banner-icon">{returnInfo.icon}</span>
          <div className="od-return-banner-content">
            <h4>Yêu cầu đổi/trả hàng: {returnInfo.label}</h4>
            <p><strong>Lý do:</strong> {existingReturn.reason}</p>
            {(existingReturn.items || []).length > 0 && (
              <p>
                <strong>Sản phẩm:</strong>{' '}
                {existingReturn.items.map((it) => `${it.productName} × ${it.quantity}`).join(', ')}
              </p>
            )}
            {existingReturn.adminNote && (
              <p><strong>Phản hồi từ EcoGreen:</strong> {existingReturn.adminNote}</p>
            )}
            {existingReturn.refund && (
              <p>
                <strong>Hoàn tiền:</strong> {fmtVND(existingReturn.refund.amount)}
                {existingReturn.refund.status === 'SUCCESS' ? ' (đã hoàn tất)' : existingReturn.refund.status === 'FAILED' ? ' (thất bại)' : ' (đang xử lý)'}
              </p>
            )}
            {existingReturn.status === 'INFO_REQUESTED' && (
              <p>Vui lòng liên hệ EcoGreen để bổ sung thông tin theo phản hồi ở trên.</p>
            )}
          </div>
        </div>
      )}

      {/* Header & Status Card */}
      <div className="od-header-card">
        <div className="od-header-top">
          <div>
            <h1 className="od-order-id">Đơn hàng #{order.id}</h1>
            <p className="od-order-date">
              Ngày đặt: {new Date(order.createdAt).toLocaleString('vi-VN')}
            </p>
          </div>
          <span className={`od-status-tag ${order.status}`}>
            {statusInfo.icon} {statusInfo.label}
          </span>
        </div>

        {/* Timeline (Hidden if cancelled) */}
        {!isCancelled && (
          <div className="od-timeline">
            <div className={`od-timeline-step ${getStepClass(1)}`}>
              <div className="od-step-circle">📝</div>
              <span className="od-step-label">Đặt hàng</span>
            </div>
            <div className={`od-timeline-step ${getStepClass(2)}`}>
              <div className="od-step-circle">✔</div>
              <span className="od-step-label">Xác nhận</span>
            </div>
            <div className={`od-timeline-step ${getStepClass(3)}`}>
              <div className="od-step-circle">🌿</div>
              <span className="od-step-label">Đóng gói xanh</span>
            </div>
            <div className={`od-timeline-step ${getStepClass(4)}`}>
              <div className="od-step-circle">🚚</div>
              <span className="od-step-label">Đang giao</span>
            </div>
            <div className={`od-timeline-step ${getStepClass(5)}`}>
              <div className="od-step-circle">🎉</div>
              <span className="od-step-label">Hoàn tất</span>
            </div>
          </div>
        )}
      </div>

      {/* Recipient & Address Info */}
      <div className="od-info-card">
        <h2 className="od-card-title">📍 Thông tin nhận hàng</h2>
        <div className="od-info-grid">
          <div className="od-info-item">
            <label>Người nhận</label>
            <span>{order.customerName}</span>
          </div>
          <div className="od-info-item">
            <label>Số điện thoại</label>
            <span>{order.customerPhone}</span>
          </div>
          <div className="od-info-item">
            <label>Địa chỉ giao hàng</label>
            <span>{order.shippingAddress}</span>
          </div>
        </div>
      </div>

      {/* Items List */}
      <div className="od-items-card">
        <h2 className="od-card-title">🛍️ Danh sách sản phẩm sinh thái</h2>
        <div className="od-items-list">
          {order.items.map((item) => (
            <div key={item.id} className="od-item-row">
              <img
                src={resolveProductImage(item.productImage)}
                alt={item.productName}
                className="od-item-thumb"
              />
              <div className="od-item-details">
                <p className="od-item-name">{item.productName}</p>
                <p className="od-item-meta">
                  {item.quantity} × {fmtVND(item.price)}
                </p>
              </div>
              <span className="od-item-subtotal">
                {fmtVND(item.price * item.quantity)}
              </span>
            </div>
          ))}
        </div>

        <div className="od-summary-section">
          <div className="od-summary-row">
            <span>Tạm tính</span>
            <span>{fmtVND(order.subtotal ?? order.totalPrice)}</span>
          </div>
          {Number(order.discountAmount) > 0 && (
            <div className="od-summary-row">
              <span>Giảm giá{order.discountCode ? ` (${order.discountCode})` : ''}</span>
              <span>-{fmtVND(order.discountAmount)}</span>
            </div>
          )}
          <div className="od-summary-row">
            <span>Phí đóng gói & giao hàng xanh</span>
            {Number(order.shippingFee) > 0
              ? <span>{fmtVND(order.shippingFee)}</span>
              : <span style={{ color: '#2e7d32', fontWeight: 600 }}>Miễn phí 🌿</span>}
          </div>
          <div className="od-summary-row total">
            <span>Tổng thanh toán</span>
            <span>{fmtVND(order.totalPrice)}</span>
          </div>
        </div>

        {/* Actions Bar */}
        <div className="od-actions-bar">
          {order.status === 'PENDING_PAYMENT' && (
            <Link to={`/payment/${order.id}`} className="od-btn-pay">
              💳 Thanh toán ngay ({fmtVND(order.totalPrice)})
            </Link>
          )}

          {order.canCancel && (
            <button className="od-btn-cancel" onClick={handleCancel} disabled={cancelling}>
              {cancelling ? 'Đang hủy...' : '❌ Hủy đơn hàng'}
            </button>
          )}

          {canReturn && (
            <button
              className="od-btn-return"
              onClick={openReturnModal}
            >
              ♻️ Yêu cầu Đổi / Trả hàng (Chính sách 7 ngày)
            </button>
          )}
        </div>
      </div>

      {/* Return Request Modal */}
      {showReturnModal && (
        <div className="od-modal-backdrop" onClick={() => setShowReturnModal(false)}>
          <div className="od-modal-box" onClick={(e) => e.stopPropagation()}>
            <div className="od-modal-header">
              <h3>♻️ Yêu cầu Đổi / Trả hàng</h3>
              <button
                className="od-modal-close"
                onClick={() => setShowReturnModal(false)}
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleSubmitReturn} className="od-modal-body">
              <p style={{ fontSize: '0.88rem', color: '#6b7280', margin: '0 0 12px' }}>
                Đơn hàng #{order.id} được bảo vệ theo chính sách đổi trả 7 ngày của EcoGreen.
                Vui lòng cung cấp lý do để chúng tôi hỗ trợ bạn nhanh nhất.
              </p>

              <div className="od-form-group">
                <label>Lý do đổi / trả</label>
                <select
                  value={returnReason}
                  onChange={(e) => setReturnReason(e.target.value)}
                  required
                >
                  <option value="Sản phẩm bị lỗi / vỡ khi vận chuyển">
                    Sản phẩm bị lỗi / vỡ khi vận chuyển
                  </option>
                  <option value="Giao sai mẫu mã hoặc kích cỡ">
                    Giao sai mẫu mã hoặc kích cỡ
                  </option>
                  <option value="Sản phẩm không đúng với mô tả">
                    Sản phẩm không đúng với mô tả
                  </option>
                  <option value="Bao bì bị rách / hư hại">
                    Bao bì bị rách / hư hại
                  </option>
                  <option value="Khác">Lý do khác</option>
                </select>
              </div>

              <div className="od-form-group">
                <label>Mô tả chi tiết tình trạng</label>
                <textarea
                  rows="3"
                  placeholder="Mô tả cụ thể vấn đề bạn gặp phải với sản phẩm..."
                  value={returnDesc}
                  onChange={(e) => setReturnDesc(e.target.value)}
                  required
                />
              </div>

              <div className="od-form-group">
                <label>Sản phẩm cần trả</label>
                {order.items.map((it) => (
                  <div key={it.id} className="od-return-item">
                    <span>{it.productName} <small>(đã mua {it.quantity})</small></span>
                    <input
                      type="number"
                      min="0"
                      max={it.quantity}
                      value={returnQty[it.id] ?? 0}
                      onChange={(e) => {
                        const v = Math.max(0, Math.min(it.quantity, Math.floor(Number(e.target.value) || 0)));
                        setReturnQty({ ...returnQty, [it.id]: v });
                      }}
                      aria-label={`Số lượng trả của ${it.productName}`}
                    />
                  </div>
                ))}
              </div>

              <div className="od-form-group">
                <label>Ảnh chụp thực tế (1–{RETURN_MAX_IMAGES} ảnh, JPG/PNG/WebP, tối đa 5 MB mỗi ảnh)</label>
                <input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={handlePickFiles} />
                <div className="od-return-previews">
                  {returnFiles.map((f, i) => (
                    <div key={`${f.name}-${i}`} className="od-return-preview">
                      <img src={URL.createObjectURL(f)} alt={f.name} />
                      <button
                        type="button"
                        onClick={() => setReturnFiles(returnFiles.filter((_, k) => k !== i))}
                        aria-label="Xóa ảnh"
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                </div>
              </div>

              {returnError && <p style={{ color: '#c62828', fontSize: '0.88rem' }}>{returnError}</p>}

              <div className="od-modal-actions">
                <button
                  type="button"
                  className="od-btn-cancel"
                  onClick={() => setShowReturnModal(false)}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="od-btn-submit"
                  disabled={submittingReturn}
                >
                  {submittingReturn ? 'Đang gửi...' : 'Gửi yêu cầu'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default OrderDetail;
