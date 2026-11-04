import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import SocialAuthButtons from '../components/SocialAuthButtons';
import { authErrorMessage, passwordProblem } from '../utils/authErrors';
import './Login.css';

const USERNAME_RULE = /^[A-Za-z0-9_.]{3,50}$/;

const Register = () => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { register } = useAuth();
  const navigate = useNavigate();

  const goHome = (user) => navigate(user.roles?.includes('ADMIN') ? '/admin' : '/');

  const handleStandardRegister = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!USERNAME_RULE.test(username.trim())) {
      setError('Tên đăng nhập gồm 3–50 ký tự: chữ cái không dấu, số, dấu gạch dưới hoặc dấu chấm (không có khoảng trắng hay @).');
      return;
    }
    const problem = passwordProblem(password);
    if (problem) {
      setError(problem);
      return;
    }
    if (password !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp.');
      return;
    }

    setSubmitting(true);
    try {
      await register(username.trim(), email.trim(), password);
      setSuccessMsg('Đăng ký tài khoản thành công! Đang chuyển hướng sang trang Đăng nhập...');
      setTimeout(() => {
        navigate('/login');
      }, 1500);
    } catch (err) {
      setError(authErrorMessage(err, 'Đăng ký thất bại. Vui lòng thử lại.'));
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
              Gia Nhập Cộng Đồng Sống Xanh & Nhận Ưu Đãi Chào Bạn Mới
            </h1>
            <p className="brand-subtext">
              Tạo tài khoản EcoGreen ngay hôm nay để tích điểm sinh thái, nhận voucher giảm 20% đơn đầu tiên và bảo vệ hành tinh xanh của chúng ta.
            </p>

            <div className="eco-stats-pill">
              <div className="stat-item">
                <strong>Voucher</strong>
                <span>Giảm 20% bạn mới</span>
              </div>
              <div className="stat-separator"></div>
              <div className="stat-item">
                <strong>0đ Phí</strong>
                <span>Miễn phí đăng ký</span>
              </div>
              <div className="stat-separator"></div>
              <div className="stat-item">
                <strong>1-Click</strong>
                <span>Đăng ký qua Google</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Side: Shopee-Style Register Card */}
        <div className="shopee-auth-card-side">
          <div className="shopee-login-card">
            <div className="shopee-card-header">
              <h2 className="shopee-form-title">Đăng ký</h2>
            </div>

            {error && (
              <div className="shopee-alert-box error" role="alert">
                <span className="alert-icon">⚠️</span>
                <span>{error}</span>
              </div>
            )}

            {successMsg && (
              <div className="shopee-alert-box" style={{ background: '#ecfdf5', color: '#047857', border: '1px solid #a7f3d0' }}>
                <span className="alert-icon">✅</span>
                <span>{successMsg}</span>
              </div>
            )}

            {/* Social Fast Signup */}
            <div style={{ marginBottom: '8px' }}>
              <SocialAuthButtons googleText="signup_with" onSuccess={goHome} onError={setError} />
            </div>

            {/* Shopee Divider: HOẶC */}
            <div className="shopee-divider">
              <span className="divider-line"></span>
              <span className="divider-text">HOẶC ĐĂNG KÝ VỚI EMAIL</span>
              <span className="divider-line"></span>
            </div>

            <form onSubmit={handleStandardRegister} className="shopee-form">
              <div className="shopee-input-group">
                <input
                  type="text"
                  placeholder="Tên đăng nhập (viết liền không dấu)"
                  autoComplete="username"
                  maxLength={50}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="shopee-input"
                  required
                />
              </div>

              <div className="shopee-input-group">
                <input
                  type="email"
                  placeholder="Địa chỉ Email"
                  autoComplete="email"
                  maxLength={100}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="shopee-input"
                  required
                />
              </div>

              <div className="shopee-input-group password-group">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Mật khẩu (tối thiểu 8 ký tự)"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="shopee-input"
                  style={{ paddingRight: '45px' }}
                  required
                />
                <button
                  type="button"
                  className="shopee-eye-toggle"
                  style={{ right: '12px' }}
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
              </div>

              <div className="shopee-input-group password-group">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Xác nhận lại Mật khẩu"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="shopee-input"
                  style={{ paddingRight: '45px' }}
                  required
                />
              </div>

              <button
                type="submit"
                className="shopee-btn-submit"
                disabled={submitting}
              >
                {submitting ? 'ĐANG TẠO TÀI KHOẢN...' : 'ĐĂNG KÝ'}
              </button>
            </form>

            {/* Terms disclaimer */}
            <p className="shopee-legal-note">
              Bằng việc đăng ký, bạn xác nhận đã đồng ý với <Link to="/terms">Điều khoản dịch vụ</Link> & <Link to="/privacy">Chính sách bảo mật</Link> của EcoGreen.
            </p>

            {/* Switch to Login */}
            <div className="shopee-switch-auth">
              <span>Bạn đã có tài khoản EcoGreen?</span>
              <Link to="/login" className="shopee-register-link">
                Đăng nhập
              </Link>
            </div>
          </div>
        </div>
      </div>

    </div>
  );
};

export default Register;
