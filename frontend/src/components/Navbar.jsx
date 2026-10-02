import React, { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import './Navbar.css';

const Navbar = ({ searchTerm, setSearchTerm }) => {
  const { totalItems } = useCart();
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = () => {
    setDropdownOpen(false);
    logout();
    navigate('/login');
  };

  return (
    <nav className="navbar-container">
      <div className="navbar-content">
        {/* Logo */}
        <Link to="/" className="navbar-logo">
          <span className="logo-leaf">🌿</span>
          <span className="logo-text">Eco<span className="logo-green">Green</span></span>
        </Link>

        {/* Search Bar */}
        <div className="navbar-search-wrapper">
          <span className="search-icon">🔍</span>
          <input
            type="text"
            placeholder="Tìm kiếm sản phẩm sinh thái, tái chế..."
            value={searchTerm || ''}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="navbar-search-input"
          />
          {searchTerm && (
            <button
              className="search-clear-btn"
              onClick={() => setSearchTerm('')}
              title="Xóa tìm kiếm"
            >
              ✕
            </button>
          )}
        </div>

        {/* Navigation Actions */}
        <div className="navbar-actions">
          {/* Support Link */}
          <Link to="/faq" className="nav-action-link" title="Câu hỏi thường gặp & Hỗ trợ">
            <span className="nav-action-icon">💬</span>
            <span>Hỗ Trợ</span>
          </Link>

          {/* Cart Button */}
          <Link to="/cart" className="navbar-cart" title="Giỏ hàng của bạn">
            <span className="cart-icon">🛒</span>
            {totalItems > 0 && <span className="cart-badge">{totalItems}</span>}
          </Link>

          {/* User Section with modern Dropdown */}
          {user ? (
            <div className="navbar-user-dropdown" ref={dropdownRef}>
              <button
                className="user-pill-btn"
                onClick={() => setDropdownOpen(!dropdownOpen)}
                aria-expanded={dropdownOpen}
                title="Tài khoản của bạn"
              >
                <div className="user-avatar-circle">
                  {(user.username || 'U').charAt(0).toUpperCase()}
                </div>
                <span className="user-name-label">{user.username}</span>
                {isAdmin && <span className="admin-chip">Quản trị viên</span>}
                <span className={`dropdown-arrow ${dropdownOpen ? 'open' : ''}`}>▾</span>
              </button>

              {dropdownOpen && (
                <div className="dropdown-menu-box">
                  <div className="dropdown-user-header">
                    <p className="dropdown-username">{user.username}</p>
                    <p className="dropdown-user-role">
                      {isAdmin ? '🌿 Quản trị viên hệ thống' : '🛒 Khách hàng thân thiết'}
                    </p>
                  </div>

                  <div className="dropdown-divider" />

                  <Link
                    to="/orders"
                    className="dropdown-item"
                    onClick={() => setDropdownOpen(false)}
                  >
                    <span className="item-icon">📦</span>
                    <span>Đơn hàng của tôi</span>
                  </Link>

                  <Link
                    to="/policy/returns"
                    className="dropdown-item"
                    onClick={() => setDropdownOpen(false)}
                  >
                    <span className="item-icon">♻️</span>
                    <span>Chính sách đổi trả 7 ngày</span>
                  </Link>

                  <Link
                    to="/contact"
                    className="dropdown-item"
                    onClick={() => setDropdownOpen(false)}
                  >
                    <span className="item-icon">📞</span>
                    <span>Liên hệ EcoGreen</span>
                  </Link>

                  {isAdmin && (
                    <Link
                      to="/admin"
                      className="dropdown-item admin-highlight"
                      onClick={() => setDropdownOpen(false)}
                    >
                      <span className="item-icon">⚙️</span>
                      <span>Trang quản trị (Admin CMS)</span>
                    </Link>
                  )}

                  <div className="dropdown-divider" />

                  <button className="dropdown-item logout-btn" onClick={handleLogout}>
                    <span className="item-icon">🚪</span>
                    <span>Đăng xuất</span>
                  </button>
                </div>
              )}
            </div>
          ) : (
            <div className="navbar-auth-buttons">
              <Link to="/login" className="btn-nav-login">
                Đăng nhập
              </Link>
              <Link to="/register" className="btn-nav-register">
                Đăng ký
              </Link>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
