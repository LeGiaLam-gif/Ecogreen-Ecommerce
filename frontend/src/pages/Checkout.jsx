import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { checkout, previewCheckout } from '../services/orderApi';
import { getAddresses } from '../services/addressApi';

const DEBOUNCE_MS = 500;
const NEW_ADDRESS = 'new';

const formatVnd = (value) => `${Number(value || 0).toLocaleString('vi-VN')} ₫`;

const labelStyle = { display: 'block', marginBottom: '6px', fontWeight: 600, color: '#334155' };
const inputStyle = { width: '100%', padding: '11px', border: '1px solid #cbd5e1', borderRadius: '6px', boxSizing: 'border-box', fontSize: '0.95rem' };
const errorTextStyle = { color: '#c62828', fontSize: '0.9rem', margin: '4px 0 0' };

/**
 * Checkout (B04). Every amount on this page - items, subtotal, discount, shipping, total - is what POST /checkout/preview
 * returned; nothing is added up here. The preview is requested once on arrival and again when the discount code settles
 * (500 ms after the last keystroke), and a request that has been overtaken is aborted and ignored.
 */
const Checkout = () => {
  const { cartItems, resetCart } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [addresses, setAddresses] = useState([]);
  const [selectedAddress, setSelectedAddress] = useState(NEW_ADDRESS);
  const [customerName, setCustomerName] = useState(user?.username || '');
  const [customerPhone, setCustomerPhone] = useState('');
  const [shippingAddress, setShippingAddress] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('COD');

  const [codeInput, setCodeInput] = useState('');
  const [debouncedCode, setDebouncedCode] = useState('');
  const [previewNonce, setPreviewNonce] = useState(0);
  const [previewState, setPreviewState] = useState({ key: null, data: null, error: '' });

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const submittingRef = useRef(false);

  const hasItems = cartItems.length > 0;

  // Saved addresses: pick the default one for the customer; without any, the text form below stays in use.
  useEffect(() => {
    let cancelled = false;
    getAddresses()
      .then((list) => {
        if (cancelled || !Array.isArray(list) || list.length === 0) return;
        setAddresses(list);
        setSelectedAddress((list.find((a) => a.isDefault) || list[0]).id);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  // The discount code settles 500 ms after the last keystroke; only then is the preview asked again.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedCode(codeInput.trim()), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [codeInput]);

  const requestKey = `${debouncedCode}#${previewNonce}`;
  useEffect(() => {
    if (!hasItems) return undefined;
    const controller = new AbortController();
    let cancelled = false;
    previewCheckout(debouncedCode, controller.signal)
      .then((data) => {
        if (!cancelled) setPreviewState({ key: requestKey, data, error: '' });
      })
      .catch((err) => {
        if (!cancelled) {
          setPreviewState({ key: requestKey, data: null, error: err.friendlyMessage || 'Không thể tính tổng đơn hàng.' });
        }
      });
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [hasItems, debouncedCode, requestKey]);

  if (!hasItems) {
    return (
      <div className="cart-empty-container">
        <div className="cart-empty-icon">🛍️</div>
        <h2>Giỏ hàng của bạn đang trống</h2>
        <p>Vui lòng chọn sản phẩm vào giỏ trước khi tiến hành đặt hàng.</p>
        <button className="cart-back-home" onClick={() => navigate('/')}>Quay lại cửa hàng</button>
      </div>
    );
  }

  const usingSavedAddress = addresses.length > 0 && selectedAddress !== NEW_ADDRESS;
  const preview = previewState.data;
  const previewLoading = previewState.key !== requestKey;
  const codePending = codeInput.trim() !== debouncedCode;
  const issues = preview?.issues ?? [];
  const lineIssues = (productId) => issues.filter((issue) => issue.productId === productId);
  const generalIssues = issues.filter((issue) => issue.productId == null);
  // The order is only offered for the exact numbers the customer is looking at, and only when the server sees no problem.
  const canSubmit = !submitting && !previewLoading && !codePending && Boolean(preview) && issues.length === 0;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (submittingRef.current || !canSubmit) return; // double-submit guard: the ref blocks a second click before the state updates
    submittingRef.current = true;
    setError('');
    setSubmitting(true);
    try {
      const payload = { paymentMethod };
      if (debouncedCode) payload.discountCode = debouncedCode;
      if (usingSavedAddress) {
        payload.addressId = selectedAddress;
      } else {
        Object.assign(payload, { customerName, customerPhone, shippingAddress });
      }
      const order = await checkout(payload);
      navigate(`/payment/${order.id}`);
      resetCart(); // the server emptied the cart in the same transaction; no request needed
    } catch (err) {
      setError(err.friendlyMessage || 'Không thể tạo đơn hàng. Vui lòng thử lại sau.');
      setPreviewNonce((n) => n + 1); // read the server's numbers and per-product issues again
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '620px', margin: '40px auto', padding: '28px', background: '#fff', borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
      <h2 style={{ marginBottom: '24px', color: '#1b3a1f', fontSize: '1.4rem' }}>📦 Thông tin giao hàng & Đặt hàng</h2>

      {error && <p style={{ color: '#c62828', backgroundColor: '#fde8e8', padding: '10px 14px', borderRadius: '6px', marginBottom: '16px' }}>{error}</p>}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {addresses.length > 0 && (
          <div>
            <span style={labelStyle}>Địa chỉ giao hàng</span>
            {addresses.map((a) => (
              <label key={a.id} style={{ display: 'flex', gap: '10px', alignItems: 'flex-start', padding: '10px', border: '1px solid #cbd5e1', borderRadius: '6px', marginBottom: '8px', cursor: 'pointer' }}>
                <input type="radio" name="address" checked={selectedAddress === a.id} onChange={() => setSelectedAddress(a.id)} />
                <span>
                  <strong>{a.recipientName}</strong> · {a.phone}{a.isDefault ? ' (Mặc định)' : ''}
                  <br />
                  {[a.line1, a.ward, a.district, a.province].join(', ')}
                </span>
              </label>
            ))}
            <label style={{ display: 'flex', gap: '10px', alignItems: 'center', padding: '10px', border: '1px solid #cbd5e1', borderRadius: '6px', cursor: 'pointer' }}>
              <input type="radio" name="address" checked={selectedAddress === NEW_ADDRESS} onChange={() => setSelectedAddress(NEW_ADDRESS)} />
              <span>Nhập địa chỉ khác</span>
            </label>
          </div>
        )}

        {!usingSavedAddress && (
          <>
            <div>
              <label style={labelStyle}>Họ và tên người nhận *</label>
              <input
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                placeholder="Ví dụ: Nguyễn Văn A"
                required
                maxLength={100}
                style={inputStyle}
              />
            </div>
            <div>
              <label style={labelStyle}>Số điện thoại nhận hàng *</label>
              <input
                value={customerPhone}
                onChange={(e) => setCustomerPhone(e.target.value)}
                placeholder="Ví dụ: 0987654321"
                required
                maxLength={20}
                style={inputStyle}
              />
            </div>
            <div>
              <label style={labelStyle}>Địa chỉ nhận hàng chi tiết *</label>
              <textarea
                value={shippingAddress}
                onChange={(e) => setShippingAddress(e.target.value)}
                placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành phố..."
                required
                rows={3}
                maxLength={500}
                style={{ ...inputStyle, fontFamily: 'inherit' }}
              />
            </div>
          </>
        )}

        <div>
          <label style={labelStyle}>Phương thức thanh toán</label>
          <select
            value={paymentMethod}
            onChange={(e) => setPaymentMethod(e.target.value)}
            style={{ width: '100%', padding: '11px', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.95rem' }}
          >
            <option value="COD">💵 Thanh toán khi nhận hàng (COD)</option>
            <option value="VIETQR">🏦 Chuyển khoản VietQR / MoMo / VNPAY</option>
          </select>
        </div>

        <div>
          <label style={labelStyle}>Mã giảm giá</label>
          <input
            value={codeInput}
            onChange={(e) => setCodeInput(e.target.value)}
            placeholder="Nhập mã nếu có"
            maxLength={30}
            autoComplete="off"
            style={inputStyle}
          />
          {generalIssues.map((issue) => (
            <p key={issue.code} style={errorTextStyle}>{issue.message}</p>
          ))}
        </div>

        <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: '16px', opacity: previewLoading ? 0.6 : 1 }}>
          <h3 style={{ margin: '0 0 12px', fontSize: '1.05rem', color: '#1b3a1f' }}>Đơn hàng của bạn</h3>

          {previewState.key === requestKey && previewState.error && (
            <p style={errorTextStyle}>
              {previewState.error}{' '}
              <button type="button" onClick={() => setPreviewNonce((n) => n + 1)} style={{ border: 'none', background: 'none', color: '#2e7d32', cursor: 'pointer', textDecoration: 'underline' }}>
                Thử lại
              </button>
            </p>
          )}
          {!preview && !previewState.error && <p style={{ color: '#64748b' }}>Đang tính tổng đơn hàng...</p>}

          {preview && (
            <>
              {preview.items.map((line) => (
                <div key={line.productId} style={{ marginBottom: '10px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px' }}>
                    <span>{line.name} × {line.quantity}</span>
                    <span>{formatVnd(line.lineTotal)}</span>
                  </div>
                  {lineIssues(line.productId).map((issue) => (
                    <p key={issue.code} style={errorTextStyle}>{issue.message}</p>
                  ))}
                </div>
              ))}

              <div style={{ borderTop: '1px solid #e2e8f0', marginTop: '12px', paddingTop: '12px', display: 'flex', flexDirection: 'column', gap: '6px', color: '#475569' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Tạm tính</span>
                  <span>{formatVnd(preview.subtotal)}</span>
                </div>
                {Number(preview.discountAmount) > 0 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>Giảm giá</span>
                    <span>-{formatVnd(preview.discountAmount)}</span>
                  </div>
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Phí vận chuyển</span>
                  <span>{Number(preview.shippingFee) > 0 ? formatVnd(preview.shippingFee) : 'Miễn phí 🌿'}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '6px' }}>
                  <span style={{ fontSize: '1.05rem' }}>Tổng cộng:</span>
                  <span style={{ fontSize: '1.35rem', fontWeight: 'bold', color: '#2e7d32' }}>{formatVnd(preview.total)}</span>
                </div>
              </div>
            </>
          )}
        </div>

        <button
          type="submit"
          className="btn-primary"
          disabled={!canSubmit}
          style={{ padding: '13px', border: 'none', borderRadius: '6px', cursor: canSubmit ? 'pointer' : 'not-allowed', fontSize: '1.05rem', fontWeight: 'bold', backgroundColor: canSubmit ? '#2e7d32' : '#94a3b8', color: '#fff' }}
        >
          {submitting ? 'ĐANG XỬ LÝ ĐẶT HÀNG...' : 'XÁC NHẬN ĐẶT HÀNG'}
        </button>
      </form>
    </div>
  );
};

export default Checkout;
