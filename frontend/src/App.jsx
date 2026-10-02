import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, useLocation } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { CartProvider } from './context/CartContext';
import Navbar from './components/Navbar';
import LiveChatWidget from './components/LiveChatWidget';
import Home from './pages/Home';
import ProductDetail from './pages/ProductDetail';
import Login from './pages/Login';
import Register from './pages/Register';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import Payment from './pages/Payment';
import OrderSuccess from './pages/OrderSuccess';
import Orders from './pages/Orders';
import OrderDetail from './pages/OrderDetail';
import FAQ from './pages/FAQ';
import ContactUs from './pages/ContactUs';
import ReturnPolicy from './pages/ReturnPolicy';
import AdminDashboard from './pages/admin/AdminDashboard';
import ProtectedRoute from './components/ProtectedRoute';
import './App.css';

/**
 * AppContent - wraps the routes so useLocation() is available (must be
 * inside <Router>).
 */
function AppContent() {
  const [searchTerm, setSearchTerm] = useState("");
  const location = useLocation();

  // The Admin CMS has its own layout - hide the storefront navbar there.
  const isAdminPath = location.pathname.startsWith('/admin');

  return (
    <div className="app-container">
      {!isAdminPath && <Navbar searchTerm={searchTerm} setSearchTerm={setSearchTerm} />}

      <main className="main-content">
        <Routes>
          <Route path="/" element={<Home searchTerm={searchTerm} />} />
          <Route path="/product/:id" element={<ProductDetail />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/cart" element={<Cart />} />

          {/* CSKH & Chính sách - không cần đăng nhập */}
          <Route path="/faq" element={<FAQ />} />
          <Route path="/contact" element={<ContactUs />} />
          <Route path="/policy/returns" element={<ReturnPolicy />} />

          <Route path="/checkout" element={<ProtectedRoute><Checkout /></ProtectedRoute>} />
          <Route path="/payment/:orderId" element={<ProtectedRoute><Payment /></ProtectedRoute>} />
          <Route path="/order-success/:orderId" element={<ProtectedRoute><OrderSuccess /></ProtectedRoute>} />
          <Route path="/orders" element={<ProtectedRoute><Orders /></ProtectedRoute>} />
          <Route path="/orders/:id" element={<ProtectedRoute><OrderDetail /></ProtectedRoute>} />

          {/* Admin CMS - requires the ADMIN role, re-checked on every API call server-side too. */}
          <Route
            path="/admin/*"
            element={
              <ProtectedRoute adminOnly>
                <AdminDashboard />
              </ProtectedRoute>
            }
          />
        </Routes>
      </main>

      {/* Widget hỗ trợ khách hàng - hiện toàn bộ storefront (trừ admin) */}
      {!isAdminPath && <LiveChatWidget />}
    </div>
  );
}

function App() {
  return (
    <Router>
      <AuthProvider>
        <CartProvider>
          <AppContent />
        </CartProvider>
      </AuthProvider>
    </Router>
  );
}

export default App;
