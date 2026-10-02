import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { checkout } from '../services/orderApi';

const Checkout = () => {
  const { cartItems, totalPrice, refreshCart } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [customerName, setCustomerName] = useState(user?.username || '');
  const [customerPhone, setCustomerPhone] = useState('');
  const [shippingAddress, setShippingAddress] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  if (cartItems.length === 0) {
    return (
      <div className="cart-empty-container">
        <div className="cart-empty-icon">🛍️</div>
        <h2>Giỏ hàng của bạn đang trống</h2>
        <p>Vui lòng chọn sản phẩm vào giỏ trước khi tiến hành đặt hàng.</p>
        <button className="cart-back-home" onClick={() => navigate('/')}>Quay lại cửa hàng</button>
      </div>
    );
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const order = await checkout({ customerName, customerPhone, shippingAddress, paymentMethod });
      await refreshCart();
      navigate(`/payment/${order.id}`);
    } catch (err) {
      setError(err.friendlyMessage || 'Không thể tạo đơn hàng. Vui lòng thử lại sau.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '620px', margin: '40px auto', padding: '28px', background: '#fff', borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
      <h2 style={{ marginBottom: '24px', color: '#1b3a1f', fontSize: '1.4rem' }}>📦 Thông tin giao hàng & Đặt hàng</h2>

      {error && <p style={{ color: '#c62828', backgroundColor: '#fde8e8', padding: '10px 14px', borderRadius: '6px', marginBottom: '16px' }}>{error}</p>}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div>
          <label style={{ display: 'block', marginBottom: '6px', fontWeight: 600, color: '#334155' }}>Họ và tên người nhận *</label>
          <input
            value={customerName}
            onChange={(e) => setCustomerName(e.target.value)}
            placeholder="Ví dụ: Nguyễn Văn A"
            required
            style={{ width: '100%', padding: '11px', border: '1px solid #cbd5e1', borderRadius: '6px', boxSizing: 'border-box', fontSize: '0.95rem' }}
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '6px', fontWeight: 600, color: '#334155' }}>Số điện thoại nhận hàng *</label>
          <input
            value={customerPhone}
            onChange={(e) => setCustomerPhone(e.target.value)}
            placeholder="Ví dụ: 0987654321"
            required
            style={{ width: '100%', padding: '11px', border: '1px solid #cbd5e1', borderRadius: '6px', boxSizing: 'border-box', fontSize: '0.95rem' }}
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '6px', fontWeight: 600, color: '#334155' }}>Địa chỉ nhận hàng chi tiết *</label>
          <textarea
            value={shippingAddress}
            onChange={(e) => setShippingAddress(e.target.value)}
            placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành phố..."
            required
            rows={3}
            style={{ width: '100%', padding: '11px', border: '1px solid #cbd5e1', borderRadius: '6px', boxSizing: 'border-box', fontSize: '0.95rem', fontFamily: 'inherit' }}
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '6px', fontWeight: 600, color: '#334155' }}>Phương thức thanh toán</label>
          <select
            value={paymentMethod}
            onChange={(e) => setPaymentMethod(e.target.value)}
            style={{ width: '100%', padding: '11px', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.95rem' }}
          >
            <option value="COD">💵 Thanh toán khi nhận hàng (COD)</option>
            <option value="VIETQR">🏦 Chuyển khoản VietQR / MoMo / VNPAY</option>
          </select>
        </div>

        <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: '1.05rem', color: '#475569' }}>Tổng thanh toán:</span>
          <span style={{ fontSize: '1.35rem', fontWeight: 'bold', color: '#2e7d32' }}>{Number(totalPrice).toLocaleString('vi-VN')} ₫</span>
        </div>

        <button
          type="submit"
          className="btn-primary"
          disabled={submitting}
          style={{ padding: '13px', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '1.05rem', fontWeight: 'bold', backgroundColor: '#2e7d32', color: '#fff' }}
        >
          {submitting ? 'ĐANG XỬ LÝ ĐẶT HÀNG...' : 'XÁC NHẬN ĐẶT HÀNG'}
        </button>
      </form>
    </div>
  );
};

export default Checkout;
