import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getGoogleClientId, renderGoogleSignInButton } from '../services/googleAuth';
import './Login.css';

const Register = () => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [googleUnavailable, setGoogleUnavailable] = useState(!getGoogleClientId());
  const googleBtnRef = useRef(null);

  const { register, loginWithGoogle } = useAuth();
  const navigate = useNavigate();

  const handleStandardRegister = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (password !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp.');
      return;
    }

    if (password.length < 6) {
      setError('Mật khẩu phải có ít nhất 6 ký tự.');
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
      setError(err.friendlyMessage || err.response?.data?.message || 'Đăng ký thất bại. Tên đăng nhập hoặc email đã tồn tại.');
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
      setError(err.friendlyMessage || err.response?.data?.message || 'Đăng ký qua Google không thành công. Vui lòng thử lại.');
    } finally {
      setGoogleLoading(false);
    }
  };

  useEffect(() => {
    if (!googleBtnRef.current || !getGoogleClientId()) return;
    renderGoogleSignInButton(googleBtnRef.current, {
      onIdToken: handleGoogleIdToken,
      onError: () => setError('Đăng ký qua Google không thành công. Vui lòng thử lại.'),
      text: 'signup_with'
    }).catch(() => setGoogleUnavailable(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleFacebookClick = () => {
    alert('Tính năng đăng nhập Facebook đang được bảo trì theo quy định bảo mật Meta. Vui lòng chọn "Đăng ký bằng Google" để khởi tạo tài khoản tức thì!');
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
              <div className="shopee-alert-box error">
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
            <div className="shopee-social-row" style={{ marginBottom: '8px' }}>
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
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="shopee-input"
                  required
                />
              </div>

              <div className="shopee-input-group password-group">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Thiết lập Mật khẩu (tối thiểu 6 ký tự)"
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
              Bằng việc đăng ký, bạn xác nhận đã đồng ý với <Link to="/policy/returns">Điều khoản dịch vụ</Link> & <Link to="/contact">Chính sách bảo mật</Link> của EcoGreen.
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
