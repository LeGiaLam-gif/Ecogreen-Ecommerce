import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { getOrderById } from '../services/orderApi';
import { getPaymentForOrder, payNow, updatePaymentMethod } from '../services/paymentApi';
import './Payment.css';

// Thông tin tài khoản cửa hàng EcoGreen
const STORE_PAYMENT_CONFIG = {
  bank: {
    bankCode: 'MB',
    bankName: 'MBBank (Ngân hàng Quân Đội)',
    accountNo: '0123456789',
    accountName: 'CONG TY CP ECOGREEN VIET NAM',
  },
  momo: {
    phone: '0987654321',
    name: 'CUA HANG ECOGREEN STORE',
  },
  vnpay: {
    merchantId: 'ECOGREEN_VNPAY',
    name: 'ECOGREEN E-COMMERCE',
  }
};

const METHODS = [
  {
    id: 'COD',
    icon: '💵',
    label: 'Thanh toán khi nhận hàng (COD)',
    badge: 'Phổ biến nhất (78%)',
    badgeColor: '#16a34a',
    desc: 'Không cần thanh toán trước, thanh toán tiền mặt cho shipper',
  },
  {
    id: 'VIETQR',
    icon: '🏦',
    label: 'Chuyển khoản ngân hàng (VietQR)',
    badge: 'Khuyên dùng',
    badgeColor: '#0284c7',
    desc: 'Quét mã QR tự động điền STK, số tiền qua mọi App ngân hàng',
  },
  {
    id: 'MOMO',
    icon: '🟣',
    label: 'Ví điện tử MoMo',
    badge: 'Nhanh chóng',
    badgeColor: '#a855f7',
    desc: 'Quét mã QR bằng ứng dụng MoMo để thanh toán tức thì',
  },
  {
    id: 'VNPAY',
    icon: '💳',
    label: 'Cổng thanh toán VNPAY / Thẻ ATM',
    badge: 'An toàn & Bảo mật',
    badgeColor: '#ea580c',
    desc: 'Hỗ trợ thẻ ATM nội địa, thẻ quốc tế Visa, Mastercard, VNPAY-QR',
  },
];

const fmtVND = (n) => Number(n || 0).toLocaleString('vi-VN') + ' ₫';

const Payment = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  const [selectedMethod, setSelectedMethod] = useState('VIETQR');
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState('');
  const [copiedField, setCopiedField] = useState('');
  const [vietQrImgFailed, setVietQrImgFailed] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [o, p] = await Promise.all([
        getOrderById(orderId),
        getPaymentForOrder(orderId).catch(() => null)
      ]);
      setOrder(o);
      setPayment(p);
      if (p?.paymentMethod) {
        setSelectedMethod(p.paymentMethod);
      }
    } catch (err) {
      setError(err.friendlyMessage || 'Không thể tải thông tin đơn hàng.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [orderId]); // eslint-disable-line

  const handleSelectMethod = async (methodId) => {
    setSelectedMethod(methodId);
    try {
      await updatePaymentMethod(orderId, methodId);
    } catch (err) {
      console.warn('Could not sync payment method:', err);
    }
  };

  const handlePay = async () => {
    setPaying(true);
    setError('');
    try {
      try {
        await updatePaymentMethod(orderId, selectedMethod);
      } catch (ignored) {}
      await payNow(orderId);
      navigate(`/order-success/${orderId}`);
    } catch (err) {
      setError(err.friendlyMessage || 'Thanh toán thất bại. Vui lòng thử lại.');
    } finally {
      setPaying(false);
    }
  };

  const copyToClipboard = (text, fieldName) => {
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    setTimeout(() => setCopiedField(''), 2500);
  };

  if (loading) return <div className="pay-loading"><div className="spinner" /></div>;
  if (error && !order) return <p className="pay-error-full">{error}</p>;

  // Amount is totalPrice from order or payment amount
  const amount = payment?.amount ?? order?.totalPrice ?? 0;
  const transferContent = `ECOGREEN ${orderId}`;
  const alreadyPaid = payment?.status === 'SUCCESS' || order?.status === 'PAID';

  // VietQR Deep link / image
  const vietQRUrl = `https://img.vietqr.io/image/${STORE_PAYMENT_CONFIG.bank.bankCode}-${STORE_PAYMENT_CONFIG.bank.accountNo}-compact2.jpg?amount=${amount}&addInfo=${encodeURIComponent(transferContent)}&accountName=${encodeURIComponent(STORE_PAYMENT_CONFIG.bank.accountName)}`;

  // Raw QR string for MoMo transfer
  const momoQrValue = `2|99|${STORE_PAYMENT_CONFIG.momo.phone}|${STORE_PAYMENT_CONFIG.momo.name}||0|0|${amount}|${transferContent}|transfer_myqr`;

  // Raw QR string for VietQR fallback
  const vietQrValue = `https://api.vietqr.io/${STORE_PAYMENT_CONFIG.bank.bankCode}/${STORE_PAYMENT_CONFIG.bank.accountNo}/${amount}/${encodeURIComponent(transferContent)}`;

  return (
    <div className="pay-page">
      <div className="pay-wrapper">

        {/* CỘT TRÁI: DANH SÁCH PHƯƠNG THỨC THANH TOÁN */}
        <div className="pay-left">
          <div className="pay-header-info">
            <h1 className="pay-heading">Thanh toán đơn hàng #{orderId}</h1>
            <span className="pay-order-status-badge">
              {alreadyPaid ? '✅ ĐÃ THANH TOÁN' : '⏳ CHỜ THANH TOÁN'}
            </span>
          </div>

          <div className="pay-amount-box">
            <span className="pay-amount-label">Tổng số tiền cần thanh toán</span>
            <div className="pay-amount-display">{fmtVND(amount)}</div>
            <p className="pay-amount-sub">Bao gồm thuế và phí đóng gói sinh thái EcoGreen</p>
          </div>

          <p className="pay-choose-label">Chọn hình thức thanh toán:</p>
          <div className="pay-methods">
            {METHODS.map((m) => (
              <button
                key={m.id}
                className={`pay-method-btn ${selectedMethod === m.id ? 'active' : ''}`}
                onClick={() => handleSelectMethod(m.id)}
                disabled={alreadyPaid}
                type="button"
              >
                <span className="pay-method-icon">{m.icon}</span>
                <div className="pay-method-info">
                  <div className="pay-method-title-row">
                    <strong>{m.label}</strong>
                    {m.badge && (
                      <span className="pay-method-pill" style={{ backgroundColor: `${m.badgeColor}15`, color: m.badgeColor, borderColor: `${m.badgeColor}40` }}>
                        {m.badge}
                      </span>
                    )}
                  </div>
                  <small>{m.desc}</small>
                </div>
                {selectedMethod === m.id && <span className="pay-method-check">✓</span>}
              </button>
            ))}
          </div>

          {error && <div className="pay-error">{error}</div>}

          {alreadyPaid ? (
            <div className="pay-success-badge">
              🎉 Đơn hàng này đã thanh toán thành công!
            </div>
          ) : (
            <button
              className={`pay-confirm-btn ${selectedMethod === 'COD' ? 'pay-confirm-btn--cod' : ''}`}
              onClick={handlePay}
              disabled={paying}
            >
              {paying
                ? 'Đang xử lý giao dịch...'
                : selectedMethod === 'COD'
                ? '🚚 Xác nhận đặt hàng (Thanh toán COD)'
                : '✅ Tôi đã hoàn tất chuyển khoản'}
            </button>
          )}

          <div className="pay-security-guarantee">
            <span>🔒 Giao dịch bảo mật chuẩn SSL 256-bit</span>
            <span>🌿 Bảo vệ người tiêu dùng sinh thái 7 ngày</span>
          </div>
        </div>

        {/* CỘT PHẢI: HIỂN THỊ MÃ QR CỦA CỬA HÀNG HOẶC HƯỚNG DẪN */}
        <div className="pay-right">

          {/* 1. MÃ QR CHUYỂN KHOẢN NGÂN HÀNG (VIETQR) */}
          {selectedMethod === 'VIETQR' && (
            <div className="pay-qr-card pay-qr-card--vietqr">
              <div className="qr-brand-header">
                <span className="qr-bank-logo">🏦 MBBANK</span>
                <span className="qr-system-tag">VietQR Napas 24/7</span>
              </div>
              <h3 className="pay-qr-title">Quét mã VietQR để thanh toán</h3>
              <p className="pay-qr-sub">Dùng app ngân hàng bất kỳ (Vietcombank, MB, Techcombank, BIDV...) quét mã bên dưới</p>

              <div className="qr-code-wrapper">
                {!vietQrImgFailed ? (
                  <img
                    src={vietQRUrl}
                    alt="Mã VietQR Chuyển Khoản Ngân Hàng"
                    className="pay-qr-image"
                    onError={() => setVietQrImgFailed(true)}
                  />
                ) : (
                  <div className="qr-svg-container">
                    <QRCodeSVG
                      value={vietQrValue}
                      size={200}
                      level="H"
                      includeMargin={true}
                    />
                    <div className="qr-center-logo">MB</div>
                  </div>
                )}
                <div className="qr-scan-badge">⚡ Nhận tiền tức thì trong 3 giây</div>
              </div>

              {/* Bảng chi tiết tài khoản ngân hàng */}
              <div className="pay-details-table">
                <div className="pay-detail-row">
                  <span className="pay-detail-label">Ngân hàng:</span>
                  <span className="pay-detail-value font-bold">{STORE_PAYMENT_CONFIG.bank.bankName}</span>
                </div>

                <div className="pay-detail-row highlight">
                  <span className="pay-detail-label">Số tài khoản:</span>
                  <div className="pay-detail-value-group">
                    <strong className="pay-detail-number">{STORE_PAYMENT_CONFIG.bank.accountNo}</strong>
                    <button
                      type="button"
                      className="btn-copy"
                      onClick={() => copyToClipboard(STORE_PAYMENT_CONFIG.bank.accountNo, 'stk')}
                    >
                      {copiedField === 'stk' ? '✓ Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                </div>

                <div className="pay-detail-row">
                  <span className="pay-detail-label">Chủ tài khoản:</span>
                  <span className="pay-detail-value uppercase font-bold">{STORE_PAYMENT_CONFIG.bank.accountName}</span>
                </div>

                <div className="pay-detail-row highlight">
                  <span className="pay-detail-label">Số tiền:</span>
                  <div className="pay-detail-value-group">
                    <strong className="pay-detail-amount">{fmtVND(amount)}</strong>
                    <button
                      type="button"
                      className="btn-copy"
                      onClick={() => copyToClipboard(String(amount), 'amount')}
                    >
                      {copiedField === 'amount' ? '✓ Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                </div>

                <div className="pay-detail-row highlight">
                  <span className="pay-detail-label">Nội dung chuyển khoản:</span>
                  <div className="pay-detail-value-group">
                    <strong className="pay-detail-content">{transferContent}</strong>
                    <button
                      type="button"
                      className="btn-copy"
                      onClick={() => copyToClipboard(transferContent, 'content')}
                    >
                      {copiedField === 'content' ? '✓ Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                </div>
              </div>

              <div className="pay-guide-note">
                💡 <strong>Lưu ý:</strong> Vui lòng giữ nguyên nội dung chuyển khoản <strong>{transferContent}</strong> để hệ thống EcoGreen tự động xác nhận đơn hàng của bạn.
              </div>
            </div>
          )}

          {/* 2. MÃ QR VÍ ĐIỆN TỬ MOMO */}
          {selectedMethod === 'MOMO' && (
            <div className="pay-qr-card pay-qr-card--momo">
              <div className="qr-brand-header momo-header">
                <span className="momo-brand-tag">🟣 VÍ ĐIỆN TỬ MOMO</span>
                <span className="momo-verified-badge">✔ MoMo Business EcoGreen</span>
              </div>
              <h3 className="pay-qr-title momo-title">Quét mã QR Ví MoMo của cửa hàng</h3>
              <p className="pay-qr-sub">Mở ứng dụng <strong>MoMo</strong> trên điện thoại và chọn <strong>"Quét Mã"</strong></p>

              <div className="qr-code-wrapper momo-qr-wrapper">
                <div className="qr-svg-container momo-container">
                  <QRCodeSVG
                    value={momoQrValue}
                    size={200}
                    level="H"
                    fgColor="#a50064"
                    includeMargin={true}
                  />
                  <div className="qr-center-logo momo-logo-center">momo</div>
                </div>
                <div className="qr-scan-badge momo-badge">Quét mã thanh toán trực tiếp qua MoMo</div>
              </div>

              {/* Bảng chi tiết ví MoMo cửa hàng */}
              <div className="pay-details-table momo-table">
                <div className="pay-detail-row">
                  <span className="pay-detail-label">Tài khoản MoMo:</span>
                  <div className="pay-detail-value-group">
                    <strong className="pay-detail-number">{STORE_PAYMENT_CONFIG.momo.phone}</strong>
                    <button
                      type="button"
                      className="btn-copy"
                      onClick={() => copyToClipboard(STORE_PAYMENT_CONFIG.momo.phone, 'momoPhone')}
                    >
                      {copiedField === 'momoPhone' ? '✓ Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                </div>

                <div className="pay-detail-row">
                  <span className="pay-detail-label">Tên người nhận:</span>
                  <span className="pay-detail-value font-bold">{STORE_PAYMENT_CONFIG.momo.name}</span>
                </div>

                <div className="pay-detail-row highlight">
                  <span className="pay-detail-label">Số tiền:</span>
                  <div className="pay-detail-value-group">
                    <strong className="pay-detail-amount" style={{ color: '#a50064' }}>{fmtVND(amount)}</strong>
                    <button
                      type="button"
                      className="btn-copy"
                      onClick={() => copyToClipboard(String(amount), 'momoAmount')}
                    >
                      {copiedField === 'momoAmount' ? '✓ Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                </div>

                <div className="pay-detail-row highlight">
                  <span className="pay-detail-label">Lời nhắn / Nội dung:</span>
                  <div className="pay-detail-value-group">
                    <strong className="pay-detail-content">{transferContent}</strong>
                    <button
                      type="button"
                      className="btn-copy"
                      onClick={() => copyToClipboard(transferContent, 'momoContent')}
                    >
                      {copiedField === 'momoContent' ? '✓ Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                </div>
              </div>

              <div className="pay-guide-note">
                📱 <strong>Cách chuyển:</strong> Vào app MoMo $\rightarrow$ Bấm <strong>Quét mã</strong> hoặc <strong>Chuyển tiền</strong> đến số <strong>{STORE_PAYMENT_CONFIG.momo.phone}</strong> với lời nhắn <strong>{transferContent}</strong>.
              </div>
            </div>
          )}

          {/* 3. CỔNG THANH TOÁN VNPAY / THẺ NGÂN HÀNG */}
          {selectedMethod === 'VNPAY' && (
            <div className="pay-qr-card pay-qr-card--vnpay">
              <div className="qr-brand-header vnpay-header">
                <span className="vnpay-brand-tag">💳 CỔNG VNPAY-QR</span>
                <span className="vnpay-bank-support">40+ Ngân hàng liên kết</span>
              </div>
              <h3 className="pay-qr-title">Cổng thanh toán quốc gia VNPAY</h3>
              <p className="pay-qr-sub">Hỗ trợ quét VNPAY-QR hoặc dùng thẻ ATM Nội địa / Thẻ Quốc tế (Visa, Master)</p>

              <div className="qr-code-wrapper vnpay-qr-wrapper">
                <div className="qr-svg-container vnpay-container">
                  <QRCodeSVG
                    value={`https://pay.vnpay.vn/pay?merchant=ECOGREEN&orderId=${orderId}&amount=${amount}`}
                    size={200}
                    level="H"
                    fgColor="#005baa"
                    includeMargin={true}
                  />
                  <div className="qr-center-logo vnpay-logo-center">VNPAY</div>
                </div>
                <div className="qr-scan-badge vnpay-badge">Cổng thanh toán đạt chuẩn PCI-DSS</div>
              </div>

              <div className="pay-details-table">
                <div className="pay-detail-row">
                  <span className="pay-detail-label">Đơn vị thụ hưởng:</span>
                  <span className="pay-detail-value font-bold">{STORE_PAYMENT_CONFIG.vnpay.name}</span>
                </div>
                <div className="pay-detail-row highlight">
                  <span className="pay-detail-label">Số tiền thanh toán:</span>
                  <strong className="pay-detail-amount" style={{ color: '#005baa' }}>{fmtVND(amount)}</strong>
                </div>
                <div className="pay-detail-row">
                  <span className="pay-detail-label">Mã giao dịch:</span>
                  <span className="pay-detail-value font-bold">VNPAY_{orderId}</span>
                </div>
              </div>

              <div className="pay-guide-note">
                ℹ️ Bạn có thể quét mã QR bằng ứng dụng ngân hàng hoặc bấm nút <strong>"Xác nhận đã chuyển khoản"</strong> sau khi hoàn tất.
              </div>
            </div>
          )}

          {/* 4. THANH TOÁN KHI NHẬN HÀNG (COD) */}
          {selectedMethod === 'COD' && (
            <div className="pay-qr-card pay-qr-card--cod">
              <div className="cod-header-icon">🚚</div>
              <h3 className="cod-title">Thanh toán khi nhận hàng (COD)</h3>
              <p className="cod-subtitle">Phương thức thanh toán tiện lợi & an tâm nhất của TMĐT Việt Nam</p>

              <div className="cod-steps-container">
                <div className="cod-step-item">
                  <div className="cod-step-num">1</div>
                  <div className="cod-step-text">
                    <strong>EcoGreen xác nhận đơn hàng</strong>
                    <p>Hệ thống tự động ghi nhận và chuyển đơn hàng sang trạng thái chuẩn bị đóng gói.</p>
                  </div>
                </div>

                <div className="cod-step-item">
                  <div className="cod-step-num">2</div>
                  <div className="cod-step-text">
                    <strong>Đóng gói sinh thái 100%</strong>
                    <p>Sản phẩm được gói bằng giấy kraft tái chế và không sử dụng túi nilon độc hại.</p>
                  </div>
                </div>

                <div className="cod-step-item">
                  <div className="cod-step-num">3</div>
                  <div className="cod-step-text">
                    <strong>Giao hàng tận nơi & Đồng kiểm</strong>
                    <p>Shipper giao đến địa chỉ của bạn. Bạn được quyền kiểm tra sản phẩm trước khi thanh toán.</p>
                  </div>
                </div>

                <div className="cod-step-item">
                  <div className="cod-step-num">4</div>
                  <div className="cod-step-text">
                    <strong>Thanh toán tiền mặt</strong>
                    <p>Thanh toán trực tiếp số tiền <strong>{fmtVND(amount)}</strong> cho nhân viên giao hàng.</p>
                  </div>
                </div>
              </div>

              <div className="cod-guarantee-box">
                🌿 <strong>Cam kết EcoGreen:</strong> Đổi trả miễn phí trong 7 ngày nếu sản phẩm lỗi hoặc không đúng mô tả.
              </div>
            </div>
          )}

        </div>

      </div>
    </div>
  );
};

export default Payment;
