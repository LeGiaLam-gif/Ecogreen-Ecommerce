import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getGoogleClientId, renderGoogleSignInButton } from '../services/googleAuth';
import './Login.css';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [googleUnavailable, setGoogleUnavailable] = useState(!getGoogleClientId());
  const googleBtnRef = useRef(null);
  const [forgotModal, setForgotModal] = useState(false);

  const { login, loginWithGoogle } = useAuth();
  const navigate = useNavigate();

  const handleStandardLogin = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const user = await login(username.trim(), password);
      navigate(user.roles?.includes('ADMIN') ? '/admin' : '/');
    } catch (err) {
      setError(err.friendlyMessage || err.response?.data?.message || 'Tên đăng nhập hoặc mật khẩu không chính xác.');
    } finally {
      setSubmitting(false);
    }
  };

  // Google returns a signed ID token; the backend verifies it. Only the token is sent.
  const handleGoogleIdToken = async (idToken) => {
    setGoogleLoading(true);
    setError('');
    try {
      const user = await loginWithGoogle(idToken);
      navigate(user.roles?.includes('ADMIN') ? '/admin' : '/');
    } catch (err) {
      setError(err.friendlyMessage || err.response?.data?.message || 'Đăng nhập bằng Google không thành công. Vui lòng thử lại.');
    } finally {
      setGoogleLoading(false);
    }
  };

  useEffect(() => {
    if (!googleBtnRef.current || !getGoogleClientId()) return;
    renderGoogleSignInButton(googleBtnRef.current, {
      onIdToken: handleGoogleIdToken,
      onError: () => setError('Đăng nhập bằng Google không thành công. Vui lòng thử lại.'),
      text: 'signin_with'
    }).catch(() => setGoogleUnavailable(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleFacebookClick = () => {
    alert('Tính năng đăng nhập Facebook đang được bảo trì theo quy định bảo mật Meta. Vui lòng chọn "Đăng nhập bằng Google" để vào hệ thống ngay!');
  };


  return (
    <div className="shopee-auth-page">
      <div className="shopee-auth-container">
        {/* Left Side: Eco Branding Showcase */}
        <div className="shopee-auth-brand-side">
          <div className="eco-brand-hero-content">
            <div className="brand-logo-wrap">
              <span className="brand-icon-big">🌿</span>
              <span className="brand-title-big">EcoGreen</span>
            </div>
            <h1 className="brand-tagline">
              Nền tảng Mua sắm Sản phẩm Tái chế & Tiêu dùng Xanh số 1 Việt Nam
            </h1>
            <p className="brand-subtext">
              Chung tay bảo vệ môi trường, giảm thiểu rác thải nhựa với hơn hàng nghìn sản phẩm hữu cơ, thân thiện với thiên nhiên.
            </p>

            <div className="eco-stats-pill">
              <div className="stat-item">
                <strong>100%</strong>
                <span>Chứng nhận Eco</span>
              </div>
              <div className="stat-separator"></div>
              <div className="stat-item">
                <strong>24/7</strong>
                <span>Hỗ trợ khách hàng</span>
              </div>
              <div className="stat-separator"></div>
              <div className="stat-item">
                <strong>7 Ngày</strong>
                <span>Đổi trả miễn phí</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Side: Shopee-Style Login Card */}
        <div className="shopee-auth-card-side">
          <div className="shopee-login-card">
            <div className="shopee-card-header">
              <h2 className="shopee-form-title">Đăng nhập</h2>
              <div className="shopee-qr-tip" title="Đăng nhập nhanh">
                <span className="qr-tip-text">Đăng nhập với mã QR</span>
                <span className="qr-icon-svg">🔲</span>
              </div>
            </div>

            {error && (
              <div className="shopee-alert-box error">
                <span className="alert-icon">⚠️</span>
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleStandardLogin} className="shopee-form">
              <div className="shopee-input-group">
                <input
                  type="text"
                  placeholder="Email/Số điện thoại/Tên đăng nhập"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="shopee-input"
                  required
                />
              </div>

              <div className="shopee-input-group password-group">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Mật khẩu"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="shopee-input"
                  required
                />
                <button
                  type="button"
                  className="shopee-eye-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex="-1"
                  title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? (
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  ) : (
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                      <line x1="1" y1="1" x2="23" y2="23" />
                    </svg>
                  )}
                </button>
                <button
                  type="button"
                  className="shopee-forgot-link"
                  onClick={() => setForgotModal(true)}
                  tabIndex="-1"
                >
                  Quên mật khẩu?
                </button>
              </div>

              <button
                type="submit"
                className="shopee-btn-submit"
                disabled={submitting}
              >
                {submitting ? 'ĐANG ĐĂNG NHẬP...' : 'ĐĂNG NHẬP'}
              </button>

              <div className="shopee-options-row">
                <label className="shopee-checkbox-label">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                  />
                  <span>Duy trì đăng nhập</span>
                </label>
                <span className="shopee-help-tip" title="Ghi nhớ trạng thái phiên để không phải nhập lại mật khẩu">
                  (?)
                </span>
              </div>
            </form>

            {/* Shopee Divider: HOẶC */}
            <div className="shopee-divider">
              <span className="divider-line"></span>
              <span className="divider-text">HOẶC</span>
              <span className="divider-line"></span>
            </div>

            {/* Social Login Buttons: Facebook & Google */}
            <div className="shopee-social-row">
              <button
                type="button"
                className="shopee-social-btn btn-facebook"
                onClick={handleFacebookClick}
              >
                <svg className="social-icon" viewBox="0 0 24 24" width="20" height="20" fill="#1877F2">
                  <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                </svg>
                <span>Facebook</span>
              </button>

              <div className="google-signin-slot" style={{ flex: 1, minWidth: 0, display: 'flex', justifyContent: 'center' }}>
                {googleUnavailable ? (
                  <button type="button" className="shopee-social-btn btn-google" disabled title="Đăng nhập Google chưa được cấu hình">
                    <span>Google (chưa khả dụng)</span>
                  </button>
                ) : (
                  <div ref={googleBtnRef} aria-busy={googleLoading} />
                )}
              </div>
            </div>

            {/* Terms disclaimer */}
            <p className="shopee-legal-note">
              Bằng việc đăng nhập, bạn đồng ý với <Link to="/policy/returns">Điều khoản dịch vụ</Link> & <Link to="/contact">Chính sách bảo mật</Link> của EcoGreen.
            </p>

            {/* Switch to Register */}
            <div className="shopee-switch-auth">
              <span>Bạn mới biết đến EcoGreen?</span>
              <Link to="/register" className="shopee-register-link">
                Đăng ký ngay
              </Link>
            </div>
          </div>
        </div>
      </div>


      {/* Forgot Password Modal */}
      {forgotModal && (
        <div className="shopee-modal-overlay" onClick={() => setForgotModal(false)}>
          <div className="shopee-modal-box" onClick={(e) => e.stopPropagation()}>
            <div className="shopee-modal-header">
              <h3>Khôi phục mật khẩu</h3>
              <button className="btn-close-x" onClick={() => setForgotModal(false)}>✕</button>
            </div>
            <div className="shopee-modal-body">
              <p>
                Để đảm bảo an toàn tài khoản sinh thái EcoGreen, bạn có thể:
              </p>
              <ul style={{ paddingLeft: '20px', margin: '10px 0', fontSize: '0.9rem', color: '#475569' }}>
                <li>Đăng nhập nhanh và bảo mật tuyệt đối qua <strong>Google OAuth 2.0</strong>.</li>
                <li>Liên hệ Tổng đài CSKH EcoGreen <strong>1900 8888</strong> để được hỗ trợ cấp lại mật khẩu ngay lập tức.</li>
              </ul>
            </div>
            <div className="shopee-modal-footer">
              <button className="shopee-btn-submit" onClick={() => setForgotModal(false)}>
                Đã hiểu
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Login;
