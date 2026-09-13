import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyOrders } from '../services/orderApi';

const statusColor = {
  PENDING: '#f9a825',
  CONFIRMED: '#1976d2',
  PAID: '#2e7d32',
  CANCELLED: '#c62828',
};

const Orders = () => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getMyOrders()
      .then(setOrders)
      .catch((err) => setError(err.friendlyMessage || 'Could not load your orders.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="pd-loading"><div className="spinner"></div></div>;

  return (
    <div style={{ maxWidth: '800px', margin: '30px auto', padding: '0 16px' }}>
      <h2 style={{ marginBottom: '20px' }}>My orders</h2>

      {error && <p style={{ color: '#c62828' }}>{error}</p>}

      {orders.length === 0 ? (
        <div className="no-results">
          <p>You haven't placed any orders yet.</p>
          <Link to="/">Start shopping →</Link>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {orders.map((order) => (
            <Link
              to={`/orders/${order.id}`}
              key={order.id}
              style={{ textDecoration: 'none', color: 'inherit' }}
            >
              <div style={{ border: '1px solid #e0e0e0', borderRadius: '8px', padding: '16px', background: '#fff', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontWeight: 'bold' }}>Order #{order.id}</p>
                  <p style={{ color: '#666', fontSize: '0.9rem' }}>
                    {new Date(order.createdAt).toLocaleString()} · {order.items.length} item(s)
                  </p>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <p style={{ fontWeight: 'bold' }}>{Number(order.totalPrice).toLocaleString()} ₫</p>
                  <span style={{ color: statusColor[order.status] || '#333', fontWeight: 600, fontSize: '0.85rem' }}>
                    {order.status}
                  </span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default Orders;
