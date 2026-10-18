import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getPaymentForOrder } from '../services/paymentApi';
import MockPayButtons from '../components/MockPayButtons';
import './Payment.css';

const POLL_INTERVAL_MS = 2000;
const POLL_LIMIT_MS = 30000;

// The order id comes from our own return URL (?orderId=); the VNPay reference "<orderId>-<time>-<random>" is the fallback.
const readOrderId = (params) => {
  const direct = params.get('orderId') || '';
  if (/^\d+$/.test(direct)) return direct;
  const match = (params.get('vnp_TxnRef') || '').match(/^(\d+)-/);
  return match ? match[1] : null;
};

/**
 * Where the gateway sends the browser back. Returning here is NOT proof of payment: the payment is confirmed only by the
 * signed webhook on the server. This page asks the server for the payment status every 2 s for at most 30 s, then either
 * moves on (paid), reports the failure, or says the result is still being processed.
 */
const PaymentReturn = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const orderId = readOrderId(params);

  const [phase, setPhase] = useState('checking'); // checking | processing | failed
  const [payment, setPayment] = useState(null);
  const [error, setError] = useState('');
  const [round, setRound] = useState(0); // a new round restarts the polling

  useEffect(() => {
    if (!orderId) return undefined;
    let stopped = false;
    let timer = null;
    const startedAt = Date.now();

    const check = async () => {
      let lastError = '';
      try {
        const p = await getPaymentForOrder(orderId);
        if (stopped) return;
        setPayment(p);
        if (p.status === 'SUCCESS') {
          navigate(`/order-success/${orderId}`, { replace: true });
          return;
        }
        if (p.status === 'FAILED' || p.status === 'CANCELLED') {
          setPhase('failed');
          return;
        }
      } catch (err) {
        if (stopped) return;
        lastError = err.friendlyMessage || 'Không thể kiểm tra kết quả thanh toán.';
      }
      if (Date.now() - startedAt >= POLL_LIMIT_MS) {
        setError(lastError);
        setPhase('processing');
        return;
      }
      timer = setTimeout(check, POLL_INTERVAL_MS);
    };

    check();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [orderId, navigate, round]);

  const recheck = () => {
    setPhase('checking');
    setError('');
    setRound((r) => r + 1);
  };

  if (!orderId) {
    return (
      <div className="pay-page">
        <p className="pay-error-full">Không xác định được đơn hàng cần kiểm tra thanh toán.</p>
        <Link to="/orders">Xem đơn hàng của tôi</Link>
      </div>
    );
  }

  const showMock = payment?.mockEnabled && payment.provider === 'MOCK' && payment.status === 'PENDING';

  return (
    <div className="pay-page">
      <div className="pay-wrapper" style={{ display: 'block', maxWidth: 560, margin: '40px auto', textAlign: 'center' }}>
        <h1 className="pay-heading">Kết quả thanh toán đơn hàng #{orderId}</h1>

        {phase === 'checking' && (
          <>
            <div className="spinner" />
            <p>Đang xác nhận kết quả với cổng thanh toán. Vui lòng không đóng trang này…</p>
            <p><small>Việc quay lại từ cổng thanh toán chưa phải là xác nhận; hệ thống chỉ ghi nhận khi cổng thanh toán báo về máy chủ.</small></p>
          </>
        )}

        {phase === 'processing' && (
          <>
            <p>⏳ Giao dịch đang được xử lý. Chúng tôi chưa nhận được kết quả từ cổng thanh toán.</p>
            <p><small>Nếu tiền đã bị trừ, đơn hàng sẽ tự cập nhật khi cổng thanh toán báo về. Bạn có thể kiểm tra lại sau ít phút.</small></p>
            {error && <div className="pay-error">{error}</div>}
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center', marginTop: 12 }}>
              <button type="button" className="pay-confirm-btn" onClick={recheck}>Kiểm tra lại</button>
              <Link to={`/orders/${orderId}`}>Xem đơn hàng</Link>
            </div>
          </>
        )}

        {phase === 'failed' && (
          <>
            <p>❌ Thanh toán không thành công. Đơn hàng đã được hủy và tồn kho đã được trả lại.</p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center', marginTop: 12 }}>
              <Link to={`/orders/${orderId}`}>Xem đơn hàng</Link>
              <Link to="/">Tiếp tục mua sắm</Link>
            </div>
          </>
        )}

        {showMock && <MockPayButtons orderId={orderId} onDone={recheck} />}
      </div>
    </div>
  );
};

export default PaymentReturn;
