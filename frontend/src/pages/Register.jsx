import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import GoogleOAuthModal from '../components/GoogleOAuthModal';
import { getGoogleClientId, triggerRealGoogleLogin } from '../services/googleAuth';
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
  const [isGoogleModalOpen, setIsGoogleModalOpen] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);

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

  const handleGoogleAuthorize = async (googleData) => {
    setGoogleLoading(true);
    setError('');
    try {
      const user = await loginWithGoogle(googleData);
      setIsGoogleModalOpen(false);
      navigate(user.roles?.includes('ADMIN') ? '/admin' : '/');
    } catch (err) {
      setError(err.friendlyMessage || err.response?.data?.message || 'Đăng ký qua Google không thành công. Vui lòng thử lại.');
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

      {/* Google OAuth Modal Dialog */}
      <GoogleOAuthModal
        isOpen={isGoogleModalOpen}
        onClose={() => setIsGoogleModalOpen(false)}
        onAuthorize={handleGoogleAuthorize}
        loading={googleLoading}
      />
    </div>
  );
};

export default Register;
