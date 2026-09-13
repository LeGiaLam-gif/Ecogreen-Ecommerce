import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getOrderById } from '../services/orderApi';

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
    <div style={{ maxWidth: '500px', margin: '60px auto', textAlign: 'center', padding: '24px' }}>
      <div style={{ fontSize: '3rem' }}>🌿✅</div>
      <h2>Thank you, {order.customerName}!</h2>
      <p>Your order #{order.id} has been placed successfully.</p>
      <p style={{ fontWeight: 'bold', margin: '12px 0' }}>
        Total: {Number(order.totalPrice).toLocaleString()} ₫
      </p>
      <p style={{ color: '#666' }}>Status: {order.status}</p>

      <div style={{ marginTop: '24px', display: 'flex', gap: '12px', justifyContent: 'center' }}>
        <Link to="/orders" className="btn-primary" style={{ padding: '10px 18px', borderRadius: '5px', textDecoration: 'none' }}>
          View my orders
        </Link>
        <Link to="/" style={{ padding: '10px 18px', borderRadius: '5px', textDecoration: 'none', border: '1px solid #ccc' }}>
          Continue shopping
        </Link>
      </div>
    </div>
  );
};

export default OrderSuccess;
