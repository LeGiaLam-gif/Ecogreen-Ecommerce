import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import SocialAuthButtons from '../components/SocialAuthButtons';
import { authErrorMessage } from '../utils/authErrors';
import './Login.css';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  const goHome = (user) => navigate(user.roles?.includes('ADMIN') ? '/admin' : '/');

  const handleStandardLogin = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      goHome(await login(username.trim(), password, rememberMe));
    } catch (err) {
      setError(authErrorMessage(err, 'Tên đăng nhập hoặc mật khẩu không chính xác.'));
    } finally {
      setSubmitting(false);
    }
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

        {/* Right Side: Login Card */}
        <div className="shopee-auth-card-side">
          <div className="shopee-login-card">
            <div className="shopee-card-header">
              <h2 className="shopee-form-title">Đăng nhập</h2>
            </div>

            {error && (
              <div className="shopee-alert-box error" role="alert">
                <span className="alert-icon">⚠️</span>
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleStandardLogin} className="shopee-form">
              <div className="shopee-input-group">
                <input
                  type="text"
                  placeholder="Email hoặc tên đăng nhập"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="shopee-input"
                  autoComplete="username"
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
                  autoComplete="current-password"
                  required
                />
                <button
                  type="button"
                  className="shopee-eye-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex="-1"
                  title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
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
              </div>

              <div className="shopee-forgot-row">
                <Link to="/forgot-password" className="shopee-forgot-link">
                  Quên mật khẩu?
                </Link>
              </div>

              <button type="submit" className="shopee-btn-submit" disabled={submitting}>
                {submitting ? 'ĐANG ĐĂNG NHẬP...' : 'ĐĂNG NHẬP'}
              </button>

              <div className="shopee-options-row">
                <label className="shopee-checkbox-label">
                  <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />
                  <span>Duy trì đăng nhập</span>
                </label>
                <span
                  className="shopee-help-tip"
                  title="Bỏ chọn nếu bạn dùng máy chung: phiên đăng nhập sẽ kết thúc khi đóng trình duyệt."
                >
                  (?)
                </span>
              </div>
            </form>

            <div className="shopee-divider">
              <span className="divider-line"></span>
              <span className="divider-text">HOẶC</span>
              <span className="divider-line"></span>
            </div>

            <SocialAuthButtons remember={rememberMe} onSuccess={goHome} onError={setError} />

            <p className="shopee-legal-note">
              Bằng việc đăng nhập, bạn đồng ý với <Link to="/terms">Điều khoản dịch vụ</Link> & <Link to="/privacy">Chính sách bảo mật</Link> của EcoGreen.
            </p>

            <div className="shopee-switch-auth">
              <span>Bạn mới biết đến EcoGreen?</span>
              <Link to="/register" className="shopee-register-link">
                Đăng ký ngay
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
