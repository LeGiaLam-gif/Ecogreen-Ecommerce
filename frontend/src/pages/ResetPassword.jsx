import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import * as authApi from '../services/authApi';
import { authErrorMessage, passwordProblem } from '../utils/authErrors';
import './Login.css';

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  // Keep the token in state and take it out of the address bar, so it does not stay in the URL / history / Referer.
  const [token] = useState(() => searchParams.get('token') || '');
  const navigate = useNavigate();
  useEffect(() => {
    if (searchParams.has('token')) navigate('/reset-password', { replace: true });
  }, [searchParams, navigate]);

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
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
      await authApi.resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(authErrorMessage(err, 'Không thể đặt lại mật khẩu lúc này. Vui lòng thử lại sau.'));
    } finally {
      setSubmitting(false);
    }
  };

  const renderBody = () => {
    if (!token) {
      return (
        <>
          <div className="shopee-alert-box error" role="alert">
            <span className="alert-icon">⚠️</span>
            <span>Liên kết đặt lại mật khẩu không hợp lệ hoặc đã được dùng.</span>
          </div>
          <div className="shopee-switch-auth">
            <Link to="/forgot-password" className="shopee-register-link">
              Yêu cầu liên kết mới
            </Link>
          </div>
        </>
      );
    }
    if (done) {
      return (
        <>
          <div className="shopee-alert-box success" role="status">
            <span className="alert-icon">✅</span>
            <span>Đặt lại mật khẩu thành công. Các thiết bị đang đăng nhập đã bị đăng xuất. Hãy đăng nhập bằng mật khẩu mới.</span>
          </div>
          <Link to="/login" className="shopee-btn-submit shopee-btn-link">
            ĐĂNG NHẬP
          </Link>
        </>
      );
    }
    return (
      <>
        <p className="shopee-form-hint">Nhập mật khẩu mới cho tài khoản của bạn (tối thiểu 8 ký tự).</p>

        {error && (
          <div className="shopee-alert-box error" role="alert">
            <span className="alert-icon">⚠️</span>
            <span>
              {error}{' '}
              {/hết hạn|không hợp lệ/i.test(error) && (
                <Link to="/forgot-password" className="shopee-register-link">
                  Yêu cầu liên kết mới
                </Link>
              )}
            </span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="shopee-form">
          <div className="shopee-input-group password-group">
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder="Mật khẩu mới"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="shopee-input"
              autoComplete="new-password"
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
          <div className="shopee-input-group">
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder="Xác nhận mật khẩu mới"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="shopee-input"
              autoComplete="new-password"
              required
            />
          </div>
          <button type="submit" className="shopee-btn-submit" disabled={submitting}>
            {submitting ? 'ĐANG LƯU...' : 'ĐẶT LẠI MẬT KHẨU'}
          </button>
        </form>
      </>
    );
  };

  return (
    <div className="shopee-auth-page">
      <div className="shopee-auth-container shopee-auth-container--single">
        <div className="shopee-auth-card-side">
          <div className="shopee-login-card">
            <div className="shopee-card-header">
              <h2 className="shopee-form-title">Đặt lại mật khẩu</h2>
            </div>
            {renderBody()}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ResetPassword;
