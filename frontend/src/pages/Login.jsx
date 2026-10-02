import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import GoogleOAuthModal from '../components/GoogleOAuthModal';
import { getGoogleClientId, triggerRealGoogleLogin } from '../services/googleAuth';
import './Login.css';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [isGoogleModalOpen, setIsGoogleModalOpen] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
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

  const handleGoogleAuthorize = async (googleData) => {
    setGoogleLoading(true);
    setError('');
    try {
      const user = await loginWithGoogle(googleData);
      setIsGoogleModalOpen(false);
      navigate(user.roles?.includes('ADMIN') ? '/admin' : '/');
    } catch (err) {
      setError(err.friendlyMessage || err.response?.data?.message || 'Đăng nhập bằng Google không thành công. Vui lòng thử lại.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleGoogleClick = () => {
    const clientId = getGoogleClientId();
    if (clientId) {
      setGoogleLoading(true);
      triggerRealGoogleLogin({
        onSuccess: (data) => {
          setGoogleLoading(false);
          handleGoogleAuthorize(data);
        },
        onError: () => {
          setGoogleLoading(false);
          setIsGoogleModalOpen(true);
        }
      });
    } else {
      setIsGoogleModalOpen(true);
    }
  };

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

              <button
                type="button"
                className="shopee-social-btn btn-google"
                onClick={handleGoogleClick}
              >
                <svg className="social-icon" viewBox="0 0 24 24" width="20" height="20">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
                </svg>
                <span>Google</span>
              </button>
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

      {/* Google OAuth Modal Dialog */}
      <GoogleOAuthModal
        isOpen={isGoogleModalOpen}
        onClose={() => setIsGoogleModalOpen(false)}
        onAuthorize={handleGoogleAuthorize}
        loading={googleLoading}
      />

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
