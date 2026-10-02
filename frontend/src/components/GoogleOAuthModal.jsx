import React, { useState, useEffect } from 'react';
import './GoogleOAuthModal.css';

/**
 * GoogleOAuthModal:
 * Implements the exact Google V3 Account Chooser flow as shown in user's screenshots:
 * 1. Screen 1: "Chọn tài khoản" (Account Chooser) with 05 Khánh Duy (duymai.4060@gmail.com)
 * 2. Screen 2: "Đăng nhập vào EcoGreen" (Consent & Scope Authorization)
 * 3. Screen 3: "Sử dụng một tài khoản khác" (Custom email input)
 */
const GoogleOAuthModal = ({ isOpen, onClose, onAuthorize, loading }) => {
  const [step, setStep] = useState('choose'); // 'choose' | 'consent' | 'other'
  const [accounts, setAccounts] = useState([
    {
      id: 'khanh_duy',
      name: '05 Khánh Duy',
      email: 'duymai.4060@gmail.com',
      avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80',
      initials: 'KD'
    }
  ]);
  const [selectedAccount, setSelectedAccount] = useState(accounts[0]);
  const [newEmail, setNewEmail] = useState('');
  const [newName, setNewName] = useState('');
  const [inputError, setInputError] = useState('');

  useEffect(() => {
    if (isOpen) {
      setStep('choose');
      setInputError('');
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSelectAccount = (account) => {
    setSelectedAccount(account);
    setStep('consent');
  };

  const handleCreateOtherAccount = (e) => {
    e.preventDefault();
    if (!newEmail || !newEmail.includes('@')) {
      setInputError('Vui lòng nhập địa chỉ email Google hợp lệ.');
      return;
    }
    const name = newName.trim() || newEmail.split('@')[0];
    const acc = {
      id: 'custom_' + Date.now(),
      name: name,
      email: newEmail.trim().toLowerCase(),
      avatarUrl: `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name)}`,
      initials: name.charAt(0).toUpperCase()
    };
    setAccounts((prev) => [acc, ...prev]);
    setSelectedAccount(acc);
    setStep('consent');
  };

  const handleFinalAuthorize = () => {
    onAuthorize({
      email: selectedAccount.email,
      name: selectedAccount.name,
      googleId: 'google_' + Math.abs(selectedAccount.email.split('').reduce((a, b) => ((a << 5) - a) + b.charCodeAt(0), 0)),
      avatar: selectedAccount.avatarUrl || `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(selectedAccount.name)}`
    });
  };

  return (
    <div className="google-v3-overlay" onClick={onClose}>
      <div className="google-v3-window" onClick={(e) => e.stopPropagation()}>
        {/* Google Chrome Titlebar */}
        <div className="google-v3-titlebar">
          <div className="titlebar-left">
            <svg className="google-g-small" viewBox="0 0 24 24" width="16" height="16">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
            </svg>
            <span className="titlebar-text">Đăng nhập - Tài khoản Google - Google Chrome</span>
          </div>
          <div className="titlebar-controls">
            <button className="tb-btn" title="Thu nhỏ">─</button>
            <button className="tb-btn" title="Phóng to">▢</button>
            <button className="tb-btn tb-close" onClick={onClose} title="Đóng">✕</button>
          </div>
        </div>

        {/* Chrome Address Bar */}
        <div className="google-v3-urlbar">
          <div className="url-container">
            <span className="url-lock-icon">🔒</span>
            <span className="url-hostname">accounts.google.com</span>
            <span className="url-path">/v3/signin/accountchooser?access_type=offline&client_id=549970183182-ecogreen.apps.googleusercontent.com</span>
          </div>
        </div>

        {/* Google Canvas (Gray Background with Centered MD3 Card) */}
        <div className="google-v3-canvas">
          <div className="google-md3-card">
            {/* Top Brand Banner */}
            <div className="google-card-brand">
              <svg viewBox="0 0 24 24" width="22" height="22">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
              </svg>
              <span>Đăng nhập bằng Google</span>
            </div>

            {/* SCREEN 1: CHỌN TÀI KHOẢN (Exact match to user's screenshot) */}
            {step === 'choose' && (
              <div className="google-step-choose">
                {/* App Logo */}
                <div className="app-logo-box">
                  <div className="ecogreen-app-icon">
                    <span className="eco-leaf-svg">🌿</span>
                  </div>
                </div>

                <h1 className="google-card-heading">Chọn tài khoản</h1>
                <p className="google-card-subheading">
                  Tiếp tục tới <span className="app-name-link">EcoGreen</span>
                </p>

                {/* Account List */}
                <div className="google-accounts-list">
                  {accounts.map((acc) => (
                    <div
                      key={acc.id}
                      className="account-row-item"
                      onClick={() => handleSelectAccount(acc)}
                    >
                      <div className="account-avatar-wrap">
                        {acc.avatarUrl ? (
                          <img src={acc.avatarUrl} alt={acc.name} className="account-avatar-img" />
                        ) : (
                          <div className="account-avatar-fallback">{acc.initials}</div>
                        )}
                      </div>
                      <div className="account-info-wrap">
                        <div className="account-name-text">{acc.name}</div>
                        <div className="account-email-text">{acc.email}</div>
                      </div>
                    </div>
                  ))}

                  {/* Use another account row */}
                  <div
                    className="account-row-item use-other-row"
                    onClick={() => setStep('other')}
                  >
                    <div className="account-avatar-wrap other-icon-wrap">
                      <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                        <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                      </svg>
                    </div>
                    <div className="account-info-wrap">
                      <div className="use-other-text">Sử dụng một tài khoản khác</div>
                    </div>
                  </div>
                </div>

                {/* Card Footer Info */}
                <p className="google-pre-terms">
                  Trước khi sử dụng EcoGreen, bạn có thể xem{' '}
                  <span className="link-text">Chính sách quyền riêng tư</span> và{' '}
                  <span className="link-text">Điều khoản dịch vụ</span> của ứng dụng này.
                </p>
              </div>
            )}

            {/* SCREEN 2: ỦY QUYỀN (Consent Screen) */}
            {step === 'consent' && selectedAccount && (
              <div className="google-step-consent">
                {/* App Logo */}
                <div className="app-logo-box">
                  <div className="ecogreen-app-icon">
                    <span className="eco-leaf-svg">🌿</span>
                  </div>
                </div>

                <h1 className="google-card-heading">Đăng nhập vào EcoGreen</h1>

                {/* Selected Account Pill with Dropdown toggle */}
                <div className="consent-account-pill" onClick={() => setStep('choose')} title="Chọn tài khoản khác">
                  <div className="consent-avatar">
                    {selectedAccount.avatarUrl ? (
                      <img src={selectedAccount.avatarUrl} alt={selectedAccount.name} />
                    ) : (
                      <span>{selectedAccount.initials}</span>
                    )}
                  </div>
                  <span className="consent-email">{selectedAccount.email}</span>
                  <span className="consent-arrow">▾</span>
                </div>

                <p className="consent-intro">
                  Google sẽ cho phép <strong>EcoGreen</strong> truy cập vào thông tin này về bạn:
                </p>

                {/* Scope items */}
                <div className="consent-scopes-box">
                  <div className="consent-scope-item">
                    <span className="scope-bullet-icon">👤</span>
                    <div className="scope-meta">
                      <strong>05 Khánh Duy</strong>
                      <span>Tên và ảnh hồ sơ</span>
                    </div>
                  </div>

                  <div className="consent-scope-item">
                    <span className="scope-bullet-icon">✉️</span>
                    <div className="scope-meta">
                      <strong>{selectedAccount.email}</strong>
                      <span>Địa chỉ email</span>
                    </div>
                  </div>
                </div>

                <p className="consent-legal-desc">
                  Xem <span className="link-text">Chính sách quyền riêng tư</span> và{' '}
                  <span className="link-text">Điều khoản dịch vụ</span> của EcoGreen để hiểu cách EcoGreen sẽ xử lý và bảo vệ dữ liệu của bạn.
                </p>

                <p className="consent-sub-legal">
                  Nếu bất cứ lúc nào bạn muốn thực hiện thay đổi, hãy truy cập <span className="link-text">Tài khoản Google</span> của bạn.
                </p>

                <p className="consent-learn-more">
                  Tìm hiểu thêm về tính năng <span className="link-text">Đăng nhập bằng Google</span>.
                </p>

                {/* Action Buttons: [Huỷ] [Tiếp tục] */}
                <div className="consent-buttons-row">
                  <button
                    type="button"
                    className="btn-consent-cancel"
                    onClick={() => setStep('choose')}
                    disabled={loading}
                  >
                    Huỷ
                  </button>
                  <button
                    type="button"
                    className="btn-consent-submit"
                    onClick={handleFinalAuthorize}
                    disabled={loading}
                  >
                    {loading ? (
                      <span className="inline-spinner-flex">
                        <span className="consent-spinner"></span> Đang xác thực...
                      </span>
                    ) : (
                      'Tiếp tục'
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* SCREEN 3: SỬ DỤNG TÀI KHOẢN KHÁC (Custom Input) */}
            {step === 'other' && (
              <div className="google-step-other">
                {/* App Logo */}
                <div className="app-logo-box">
                  <div className="ecogreen-app-icon">
                    <span className="eco-leaf-svg">🌿</span>
                  </div>
                </div>

                <h1 className="google-card-heading">Đăng nhập</h1>
                <p className="google-card-subheading">
                  Tiếp tục tới <span className="app-name-link">EcoGreen</span>
                </p>

                {inputError && (
                  <div className="other-error-box">
                    <span>⚠️ {inputError}</span>
                  </div>
                )}

                <form onSubmit={handleCreateOtherAccount} className="other-account-form">
                  <div className="google-input-field">
                    <label>Email hoặc số điện thoại Google</label>
                    <input
                      type="email"
                      placeholder="email@gmail.com"
                      value={newEmail}
                      onChange={(e) => setNewEmail(e.target.value)}
                      autoFocus
                      required
                    />
                  </div>

                  <div className="google-input-field">
                    <label>Tên hiển thị</label>
                    <input
                      type="text"
                      placeholder="VD: Khánh Duy"
                      value={newName}
                      onChange={(e) => setNewName(e.target.value)}
                    />
                  </div>

                  <div className="other-buttons-row">
                    <button
                      type="button"
                      className="btn-consent-cancel"
                      onClick={() => setStep('choose')}
                    >
                      Quay lại
                    </button>
                    <button type="submit" className="btn-consent-submit">
                      Tiếp theo
                    </button>
                  </div>
                </form>
              </div>
            )}
          </div>

          {/* Bottom Navigation Links */}
          <div className="google-v3-footer">
            <div className="footer-lang-selector">
              <span>Tiếng Việt</span>
              <span className="lang-caret">▾</span>
            </div>
            <div className="footer-links-group">
              <span>Trợ giúp</span>
              <span>Quyền riêng tư</span>
              <span>Điều khoản</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default GoogleOAuthModal;
