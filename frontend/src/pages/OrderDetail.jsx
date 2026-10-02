import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getOrderById } from '../services/orderApi';
import { createReturnRequest, getMyReturns } from '../services/returnApi';
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
  const [returnImage, setReturnImage] = useState('');
  const [submittingReturn, setSubmittingReturn] = useState(false);
  const [returnSuccess, setReturnSuccess] = useState('');

  useEffect(() => {
    setLoading(true);
    Promise.all([
      getOrderById(id),
      getMyReturns().catch(() => [])
    ])
      .then(([orderData, returnsData]) => {
        setOrder(orderData);
        if (Array.isArray(returnsData)) {
          const matched = returnsData.find((r) => String(r.orderId) === String(id));
          if (matched) setExistingReturn(matched);
        }
      })
      .catch((err) => setError(err.friendlyMessage || 'Không thể tải thông tin đơn hàng.'))
      .finally(() => setLoading(false));
  }, [id]);

  const handleSubmitReturn = async (e) => {
    e.preventDefault();
    setSubmittingReturn(true);
    try {
      const res = await createReturnRequest({
        orderId: id,
        reason: returnReason,
        description: returnDesc,
        imageUrl: returnImage
      });
      setExistingReturn(res);
      setShowReturnModal(false);
      setReturnSuccess('Yêu cầu đổi/trả hàng đã được gửi thành công! Đội ngũ EcoGreen sẽ liên hệ bạn sớm.');
    } catch (err) {
      alert(err.friendlyMessage || 'Không thể gửi yêu cầu đổi/trả. Vui lòng thử lại.');
    } finally {
      setSubmittingReturn(false);
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
  const getStepClass = (stepIndex) => {
    if (isCancelled) return '';
    if (order.status === 'PAID') return 'completed';
    if (order.status === 'CONFIRMED') {
      if (stepIndex <= 2) return 'completed';
      if (stepIndex === 3) return 'active';
      return '';
    }
    if (order.status === 'PENDING') {
      if (stepIndex === 1) return 'active';
      return '';
    }
    return '';
  };

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
          <span className="od-return-banner-icon">
            {existingReturn.status === 'APPROVED' ? '✅' : existingReturn.status === 'REJECTED' ? '❌' : '⏳'}
          </span>
          <div className="od-return-banner-content">
            <h4>
              Yêu cầu đổi/trả hàng:{' '}
              {existingReturn.status === 'APPROVED'
                ? 'Đã được duyệt'
                : existingReturn.status === 'REJECTED'
                ? 'Đã bị từ chối'
                : 'Đang chờ xử lý'}
            </h4>
            <p><strong>Lý do:</strong> {existingReturn.reason}</p>
            {existingReturn.adminNote && (
              <p><strong>Phản hồi từ EcoGreen:</strong> {existingReturn.adminNote}</p>
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
            {order.status === 'PAID' && '✅ Đã thanh toán & Giao thành công'}
            {order.status === 'CONFIRMED' && '📦 Đang xử lý giao hàng'}
            {order.status === 'PENDING' && '⏳ Chờ thanh toán'}
            {order.status === 'CANCELLED' && '❌ Đã hủy'}
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
            <span>{fmtVND(order.totalPrice)}</span>
          </div>
          <div className="od-summary-row">
            <span>Phí đóng gói & giao hàng xanh</span>
            <span style={{ color: '#2e7d32', fontWeight: 600 }}>Miễn phí 🌿</span>
          </div>
          <div className="od-summary-row total">
            <span>Tổng thanh toán</span>
            <span>{fmtVND(order.totalPrice)}</span>
          </div>
        </div>

        {/* Actions Bar */}
        <div className="od-actions-bar">
          {order.status === 'PENDING' && (
            <Link to={`/payment/${order.id}`} className="od-btn-pay">
              💳 Thanh toán ngay ({fmtVND(order.totalPrice)})
            </Link>
          )}

          {order.status === 'PAID' && !existingReturn && (
            <button
              className="od-btn-return"
              onClick={() => setShowReturnModal(true)}
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
                <label>Link ảnh chụp thực tế (tùy chọn)</label>
                <input
                  type="url"
                  placeholder="https://..."
                  value={returnImage}
                  onChange={(e) => setReturnImage(e.target.value)}
                />
              </div>

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
