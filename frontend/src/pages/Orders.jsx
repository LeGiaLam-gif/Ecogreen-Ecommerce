import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyOrders } from '../services/orderApi';
import { orderStatusInfo } from '../utils/orderStatus';

const Orders = () => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [meta, setMeta] = useState({ totalPages: 1 });

  useEffect(() => {
    getMyOrders({ page, size: 10 })
      .then(({ items, meta: m }) => {
        setOrders(items);
        setMeta(m);
      })
      .catch((err) => setError(err.friendlyMessage || 'Không thể tải danh sách đơn hàng.'))
      .finally(() => setLoading(false));
  }, [page]);

  if (loading) return <div className="pd-loading"><div className="spinner"></div></div>;

  return (
    <div style={{ maxWidth: '850px', margin: '30px auto', padding: '0 16px' }}>
      <h2 style={{ marginBottom: '24px', color: '#1b3a1f', fontSize: '1.5rem', fontWeight: 700 }}>
        📦 Đơn hàng của tôi
      </h2>

      {error && <p style={{ color: '#c62828', backgroundColor: '#fde8e8', padding: '10px 14px', borderRadius: '6px' }}>{error}</p>}

      {orders.length === 0 ? (
        <div className="no-results" style={{ textAlign: 'center', padding: '50px 20px', background: '#fff', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
          <div style={{ fontSize: '3rem', marginBottom: '10px' }}>🛍️</div>
          <h3 style={{ margin: '0 0 8px', color: '#334155' }}>Bạn chưa có đơn hàng nào</h3>
          <p style={{ color: '#64748b', marginBottom: '20px' }}>Hãy khám phá các sản phẩm thân thiện với môi trường của EcoGreen ngay nhé!</p>
          <Link to="/" style={{ display: 'inline-block', padding: '10px 24px', backgroundColor: '#2e7d32', color: '#fff', borderRadius: '6px', textDecoration: 'none', fontWeight: 600 }}>
            Bắt đầu mua sắm ngay →
          </Link>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {orders.map((order) => {
            const statusInfo = orderStatusInfo(order.status);
            return (
              <Link
                to={`/orders/${order.id}`}
                key={order.id}
                style={{ textDecoration: 'none', color: 'inherit' }}
              >
                <div style={{
                  border: '1px solid #e2e8f0',
                  borderRadius: '10px',
                  padding: '18px 22px',
                  background: '#fff',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
                  transition: 'all 0.2s ease',
                }}>
                  <div>
                    <p style={{ fontWeight: 700, fontSize: '1.05rem', color: '#1e293b', margin: '0 0 6px' }}>
                      Đơn hàng #{order.id}
                    </p>
                    <p style={{ color: '#64748b', fontSize: '0.88rem', margin: 0 }}>
                      Ngày đặt: {new Date(order.createdAt).toLocaleString('vi-VN')} · {order.items?.length || 0} sản phẩm
                    </p>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <p style={{ fontWeight: 700, fontSize: '1.15rem', color: '#2e7d32', margin: '0 0 6px' }}>
                      {Number(order.totalPrice).toLocaleString('vi-VN')} ₫
                    </p>
                    <span style={{
                      display: 'inline-block',
                      padding: '4px 10px',
                      borderRadius: '12px',
                      backgroundColor: statusInfo.bg,
                      color: statusInfo.color,
                      fontWeight: 600,
                      fontSize: '0.82rem',
                    }}>
                      {statusInfo.icon} {statusInfo.label}
                    </span>
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      )}

      {meta.totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px', marginTop: '20px' }}>
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Trước</button>
          <span>Trang {page + 1} / {meta.totalPages}</span>
          <button type="button" disabled={page + 1 >= meta.totalPages} onClick={() => setPage((p) => p + 1)}>Sau →</button>
        </div>
      )}
    </div>
  );
};

export default Orders;
