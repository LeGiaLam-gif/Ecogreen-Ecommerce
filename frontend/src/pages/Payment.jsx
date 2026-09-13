import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getOrderById } from '../services/orderApi';
import { getPaymentForOrder, payNow } from '../services/paymentApi';

/** Simulated payment step only - no real payment gateway is contacted. */
const Payment = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const [o, p] = await Promise.all([getOrderById(orderId), getPaymentForOrder(orderId)]);
      setOrder(o);
      setPayment(p);
    } catch (err) {
      setError(err.friendlyMessage || 'Could not load order/payment.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [orderId]);

  const handlePay = async () => {
    setPaying(true);
    setError('');
    try {
      await payNow(orderId);
      navigate(`/order-success/${orderId}`);
    } catch (err) {
      setError(err.friendlyMessage || 'Payment failed. Please try again.');
    } finally {
      setPaying(false);
    }
  };

  if (loading) return <div className="pd-loading"><div className="spinner"></div></div>;
  if (error && !order) return <p style={{ textAlign: 'center', color: '#c62828' }}>{error}</p>;

  return (
    <div style={{ maxWidth: '500px', margin: '40px auto', padding: '24px', background: '#fff', borderRadius: '10px', border: '1px solid #e0e0e0', textAlign: 'center' }}>
      <h2>Payment</h2>
      <p style={{ color: '#666', fontSize: '0.9rem' }}>
        This is a simulated payment step - no real bank or card is charged.
      </p>

      <div style={{ margin: '20px 0', textAlign: 'left', border: '1px solid #eee', borderRadius: '8px', padding: '16px' }}>
        <p><strong>Order #{order.id}</strong></p>
        <p>Method: {payment?.paymentMethod}</p>
        <p>Amount: {Number(order.totalPrice).toLocaleString()} ₫</p>
        <p>Status: {payment?.status}</p>
      </div>

      {error && <p style={{ color: '#c62828' }}>{error}</p>}

      {payment?.status === 'SUCCESS' ? (
        <button className="btn-primary" onClick={() => navigate(`/order-success/${orderId}`)}
          style={{ padding: '12px 24px', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
          View confirmation
        </button>
      ) : (
        <button className="btn-primary" onClick={handlePay} disabled={paying}
          style={{ padding: '12px 24px', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
          {paying ? 'Processing...' : 'Pay now'}
        </button>
      )}
    </div>
  );
};

export default Payment;
