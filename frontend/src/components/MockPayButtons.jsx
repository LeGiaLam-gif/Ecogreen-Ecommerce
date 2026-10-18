import React, { useState } from 'react';
import { mockPay } from '../services/paymentApi';

/**
 * TEMPORARY-COMPAT(B06): development-only buttons that simulate the provider's answer. The parent renders this only when the
 * server says the mock provider is enabled and the attempt uses it; the server refuses the call otherwise.
 * Removal condition: the project no longer needs to run the payment flow without a VNPay sandbox account.
 */
const MockPayButtons = ({ orderId, onDone, disabled = false }) => {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const run = async (outcome) => {
    setBusy(true);
    setError('');
    try {
      onDone(await mockPay(orderId, outcome));
    } catch (err) {
      setError(err.friendlyMessage || 'Không thể giả lập thanh toán.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="pay-guide-note" style={{ marginTop: 12 }}>
      <div><strong>🧪 Chế độ giả lập (chỉ dành cho phát triển)</strong></div>
      <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
        <button type="button" disabled={busy || disabled} onClick={() => run('SUCCESS')}>
          {busy ? 'Đang xử lý...' : 'Giả lập thanh toán thành công'}
        </button>
        <button type="button" disabled={busy || disabled} onClick={() => run('FAILED')}>
          Giả lập thanh toán thất bại
        </button>
      </div>
      {error && <div className="pay-error" style={{ marginTop: 8 }}>{error}</div>}
    </div>
  );
};

export default MockPayButtons;
