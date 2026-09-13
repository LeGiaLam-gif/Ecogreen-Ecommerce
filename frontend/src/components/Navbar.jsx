import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import './Navbar.css';

const Navbar = ({ searchTerm, setSearchTerm }) => {
  const { totalItems } = useCart();
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="navbar-container">
      <div className="navbar-content">
        <Link to="/" className="navbar-logo">
          🌿 EcoGreen
        </Link>

        <div className="navbar-search-wrapper">
          <input
            type="text"
            placeholder="Search eco-friendly products..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="navbar-search-input"
          />
        </div>

        <div className="navbar-actions">
          <Link to="/cart" className="navbar-cart">
            <span className="cart-icon">🛒</span>
            <span className="cart-badge">{totalItems}</span>
          </Link>

          <span className="navbar-divider"></span>

          {user ? (
            <>
              <Link to="/orders" className="navbar-link">My Orders</Link>
              <span className="navbar-divider">|</span>
              {isAdmin && (
                <>
                  <Link to="/admin" className="navbar-link" style={{ color: '#2e7d32', fontWeight: 'bold' }}>
                    Admin
                  </Link>
                  <span className="navbar-divider">|</span>
                </>
              )}
              <span className="navbar-link" style={{ fontWeight: 'bold' }}>
                👤 {user.username}
              </span>
              <span className="navbar-divider">|</span>
              <button
                onClick={handleLogout}
                className="navbar-link"
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1rem', outline: 'none', color: '#333' }}>
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="navbar-link">Log in</Link>
              <span className="navbar-divider">|</span>
              <Link to="/register" className="navbar-link">Sign up</Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
