import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import * as authApi from '../services/authApi';
import { authErrorMessage } from '../utils/authErrors';
import './Login.css';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await authApi.forgotPassword(email.trim());
      setSent(true); // the same answer whether or not the address has an account
    } catch (err) {
      setError(authErrorMessage(err, 'Không thể gửi yêu cầu lúc này. Vui lòng thử lại sau.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="shopee-auth-page">
      <div className="shopee-auth-container shopee-auth-container--single">
        <div className="shopee-auth-card-side">
          <div className="shopee-login-card">
            <div className="shopee-card-header">
              <h2 className="shopee-form-title">Quên mật khẩu</h2>
            </div>

            {sent ? (
              <>
                <div className="shopee-alert-box success" role="status">
                  <span className="alert-icon">✅</span>
                  <span>
                    Nếu email này đã đăng ký tại EcoGreen, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu. Liên kết có hiệu lực
                    trong 30 phút. Hãy kiểm tra cả hộp thư Spam.
                  </span>
                </div>
                <div className="shopee-switch-auth">
                  <Link to="/login" className="shopee-register-link">
                    Quay lại đăng nhập
                  </Link>
                </div>
              </>
            ) : (
              <>
                <p className="shopee-form-hint">Nhập email đã dùng để đăng ký. Chúng tôi sẽ gửi liên kết để bạn đặt mật khẩu mới.</p>

                {error && (
                  <div className="shopee-alert-box error" role="alert">
                    <span className="alert-icon">⚠️</span>
                    <span>{error}</span>
                  </div>
                )}

                <form onSubmit={handleSubmit} className="shopee-form">
                  <div className="shopee-input-group">
                    <input
                      type="email"
                      placeholder="Địa chỉ Email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="shopee-input"
                      autoComplete="email"
                      maxLength={100}
                      required
                    />
                  </div>
                  <button type="submit" className="shopee-btn-submit" disabled={submitting}>
                    {submitting ? 'ĐANG GỬI...' : 'GỬI LIÊN KẾT ĐẶT LẠI'}
                  </button>
                </form>

                <div className="shopee-switch-auth">
                  <Link to="/login" className="shopee-register-link">
                    Quay lại đăng nhập
                  </Link>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
