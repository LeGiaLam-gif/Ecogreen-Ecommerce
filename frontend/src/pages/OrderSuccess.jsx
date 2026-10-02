import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getOrderById } from '../services/orderApi';

const STATUS_MAP = {
  PENDING: '⏳ Chờ thanh toán',
  CONFIRMED: '📦 Đang chuẩn bị hàng',
  PAID: '✅ Đã thanh toán thành công',
  CANCELLED: '❌ Đã hủy',
};

const OrderSuccess = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);

  useEffect(() => {
    getOrderById(orderId).then(setOrder).catch(() => navigate('/'));
    // eslint-disable-next-line
  }, [orderId]);

  if (!order) return <div className="pd-loading"><div className="spinner"></div></div>;

  return (
    <div style={{ maxWidth: '540px', margin: '60px auto', textAlign: 'center', padding: '36px 24px', background: '#fff', borderRadius: '16px', border: '1px solid #e2e8f0', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
      <div style={{ fontSize: '3.5rem', marginBottom: '12px' }}>🌿🎉</div>
      <h2 style={{ color: '#1b3a1f', marginBottom: '8px', fontSize: '1.5rem' }}>Cảm ơn bạn, {order.customerName}!</h2>
      <p style={{ color: '#475569', fontSize: '1rem', margin: '0 0 16px' }}>
        Đơn hàng <strong>#{order.id}</strong> của bạn đã được khởi tạo thành công trên hệ sinh thái tiêu dùng xanh EcoGreen.
      </p>

      <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '10px', margin: '18px 0', border: '1px solid #e2e8f0' }}>
        <p style={{ fontWeight: 700, margin: '0 0 6px', fontSize: '1.1rem', color: '#2e7d32' }}>
          Tổng thanh toán: {Number(order.totalPrice).toLocaleString('vi-VN')} ₫
        </p>
        <p style={{ color: '#64748b', margin: 0, fontSize: '0.9rem' }}>
          Trạng thái: <strong>{STATUS_MAP[order.status] || order.status}</strong>
        </p>
      </div>

      <div style={{ marginTop: '28px', display: 'flex', gap: '14px', justifyContent: 'center' }}>
        <Link to="/orders" className="btn-primary" style={{ padding: '12px 22px', borderRadius: '6px', textDecoration: 'none', fontWeight: 600, backgroundColor: '#2e7d32', color: '#fff' }}>
          Xem đơn hàng của tôi
        </Link>
        <Link to="/" style={{ padding: '12px 22px', borderRadius: '6px', textDecoration: 'none', border: '1px solid #cbd5e1', color: '#334155', fontWeight: 600 }}>
          Tiếp tục mua sắm
        </Link>
      </div>
    </div>
  );
};

export default OrderSuccess;
