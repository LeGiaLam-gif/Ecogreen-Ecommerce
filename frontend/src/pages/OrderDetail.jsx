import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getOrderById } from '../services/orderApi';
import { resolveProductImage } from '../utils/imageResolver';

const OrderDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getOrderById(id)
      .then(setOrder)
      .catch((err) => setError(err.friendlyMessage || 'Could not load this order.'));
  }, [id]);

  if (error) return <p style={{ textAlign: 'center', color: '#c62828' }}>{error}</p>;
  if (!order) return <div className="pd-loading"><div className="spinner"></div></div>;

  return (
    <div style={{ maxWidth: '700px', margin: '30px auto', padding: '0 16px' }}>
      <button onClick={() => navigate('/orders')} style={{ background: 'none', border: 'none', cursor: 'pointer', marginBottom: '16px' }}>
        &#8592; Back to my orders
      </button>

      <h2>Order #{order.id}</h2>
      <p style={{ color: '#666' }}>{new Date(order.createdAt).toLocaleString()}</p>
      <p><strong>Status:</strong> {order.status}</p>

      <div style={{ border: '1px solid #eee', borderRadius: '8px', padding: '16px', margin: '16px 0' }}>
        <p><strong>Recipient:</strong> {order.customerName}</p>
        <p><strong>Phone:</strong> {order.customerPhone}</p>
        <p><strong>Address:</strong> {order.shippingAddress}</p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {order.items.map((item) => (
          <div key={item.id} style={{ display: 'flex', gap: '12px', alignItems: 'center', border: '1px solid #eee', borderRadius: '8px', padding: '10px' }}>
            <img src={resolveProductImage(item.productImage)} alt={item.productName} style={{ width: '60px', height: '60px', objectFit: 'cover', borderRadius: '6px' }} />
            <div style={{ flex: 1 }}>
              <p style={{ fontWeight: 'bold' }}>{item.productName}</p>
              <p style={{ color: '#666', fontSize: '0.9rem' }}>{item.quantity} × {Number(item.price).toLocaleString()} ₫</p>
            </div>
            <p style={{ fontWeight: 'bold' }}>{(item.price * item.quantity).toLocaleString()} ₫</p>
          </div>
        ))}
      </div>

      <div style={{ textAlign: 'right', marginTop: '16px', fontWeight: 'bold', fontSize: '1.1rem' }}>
        Total: {Number(order.totalPrice).toLocaleString()} ₫
      </div>

      {order.status === 'PENDING' && (
        <div style={{ marginTop: '16px', textAlign: 'right' }}>
          <Link to={`/payment/${order.id}`} className="btn-primary" style={{ padding: '10px 18px', borderRadius: '5px', textDecoration: 'none' }}>
            Complete payment
          </Link>
        </div>
      )}
    </div>
  );
};

export default OrderDetail;
