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
        <h2>Your cart is empty</h2>
        <p>Add something to your cart before checking out.</p>
        <button className="cart-back-home" onClick={() => navigate('/')}>Back to shop</button>
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
      setError(err.friendlyMessage || 'Could not place your order.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '600px', margin: '40px auto', padding: '24px', background: '#fff', borderRadius: '10px', border: '1px solid #e0e0e0' }}>
      <h2 style={{ marginBottom: '20px' }}>Checkout</h2>

      {error && <p style={{ color: '#c62828' }}>{error}</p>}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        <div>
          <label style={{ display: 'block', marginBottom: '5px' }}>Recipient name</label>
          <input value={customerName} onChange={(e) => setCustomerName(e.target.value)} required
            style={{ width: '100%', padding: '10px', border: '1px solid #ccc', borderRadius: '5px', boxSizing: 'border-box' }} />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '5px' }}>Phone number</label>
          <input value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} required
            style={{ width: '100%', padding: '10px', border: '1px solid #ccc', borderRadius: '5px', boxSizing: 'border-box' }} />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '5px' }}>Shipping address</label>
          <textarea value={shippingAddress} onChange={(e) => setShippingAddress(e.target.value)} required rows={3}
            style={{ width: '100%', padding: '10px', border: '1px solid #ccc', borderRadius: '5px', boxSizing: 'border-box' }} />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '5px' }}>Payment method</label>
          <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}
            style={{ width: '100%', padding: '10px', border: '1px solid #ccc', borderRadius: '5px' }}>
            <option value="COD">Cash on delivery (COD)</option>
            <option value="MOCK_PAYMENT">Pay online (simulated)</option>
          </select>
        </div>

        <div style={{ borderTop: '1px solid #eee', paddingTop: '12px', display: 'flex', justifyContent: 'space-between', fontWeight: 'bold' }}>
          <span>Total</span>
          <span>{Number(totalPrice).toLocaleString()} ₫</span>
        </div>

        <button type="submit" className="btn-primary" disabled={submitting}
          style={{ padding: '12px', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '1rem' }}>
          {submitting ? 'Placing order...' : 'Place order'}
        </button>
      </form>
    </div>
  );
};

export default Checkout;
