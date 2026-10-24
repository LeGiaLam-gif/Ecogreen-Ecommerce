import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import OverviewTab from './tabs/OverviewTab';
import ProductsTab from './tabs/ProductsTab';
import CategoriesTab from './tabs/CategoriesTab';
import OrdersTab from './tabs/OrdersTab';
import ReturnsTab from './tabs/ReturnsTab';
import UsersTab from './tabs/UsersTab';
import InventoryTab from './tabs/InventoryTab';
import DiscountsTab from './tabs/DiscountsTab';
import NotificationsTab from './tabs/NotificationsTab';
import { PERM } from '../../utils/permissions';
import './AdminDashboard.css';

// A tab is listed when the user holds ANY of its permissions (`perms` empty = every staff member). This only decides
// what is SHOWN: the backend checks the permission of every request.
const TABS = [
  { id: 'dashboard', label: '📊 Tổng quan', title: '📊 Tổng quan hệ thống (Dashboard)', perms: [] },
  { id: 'products', label: '📦 Sản phẩm', title: '📦 Quản lý Sản phẩm', perms: [PERM.PRODUCT_VIEW] },
  { id: 'categories', label: '🏷️ Danh mục', title: '🏷️ Quản lý Danh mục sản phẩm',
    perms: [PERM.CATEGORY_VIEW, PERM.CATEGORY_CREATE, PERM.CATEGORY_UPDATE, PERM.CATEGORY_DELETE] },
  { id: 'inventory', label: '🏬 Kho hàng', title: '🏬 Quản lý Tồn kho', perms: [PERM.INVENTORY_VIEW] },
  { id: 'orders', label: '📜 Đơn hàng', title: '📜 Quản lý Đơn hàng', perms: [PERM.ORDER_VIEW_ALL] },
  { id: 'returns', label: '♻️ Đổi / Trả hàng', title: '♻️ Quản lý Đổi / Trả hàng (Chính sách 7 ngày)', perms: [PERM.RETURN_VIEW] },
  { id: 'users', label: '👥 Khách hàng', title: '👥 Quản lý Khách hàng & Tài khoản', perms: [PERM.USER_VIEW] },
  { id: 'discounts', label: '🎟️ Mã giảm giá', title: '🎟️ Quản lý Mã giảm giá', perms: [PERM.DISCOUNT_VIEW] },
  { id: 'notifications', label: '✉️ Thông báo', title: '✉️ Nhật ký gửi Thông báo', perms: [PERM.ANALYTICS_VIEW] },
];

/**
 * Admin CMS shell: sidebar, top bar and the active tab. Each tab (pages/admin/tabs) owns its own data and filters.
 * The shell only keeps which tab is open, the optional starting filter a card on the dashboard asked for, and the
 * two sidebar counters the tabs report.
 */
const AdminDashboard = () => {
  // Tab mặc định khi đăng nhập quản trị luôn là Tổng quan (dashboard)
  const [activeTab, setActiveTab] = useState('dashboard');
  const [tabParams, setTabParams] = useState({});
  const [badges, setBadges] = useState({ pendingOrders: 0, pendingReturns: 0 });
  const navigate = useNavigate();
  const { user, logout, isAdmin, hasAnyPermission } = useAuth();
  const visibleTabs = TABS.filter((t) => t.perms.length === 0 || hasAnyPermission(...t.perms));

  const openTab = (tab, params = {}) => {
    setTabParams(params);
    setActiveTab(tab);
  };
  const mergeBadges = (partial) => setBadges((prev) => ({ ...prev, ...partial }));

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="admin-shell">
      {/* ─── SIDEBAR ────────────────────────────────────────────────────── */}
      <aside className="admin-sidebar">
        <div className="admin-sidebar-header">
          <h2>🌿 EcoGreen Admin</h2>
          <small>Hệ thống quản trị bán hàng</small>
        </div>

        <nav className="admin-sidebar-nav">
          {visibleTabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => openTab(tab.id)}
              className={`admin-nav-btn ${activeTab === tab.id ? 'admin-nav-btn-active' : ''}`}
            >
              {tab.label}
              {tab.id === 'orders' && badges.pendingOrders > 0 && (
                <span className="admin-nav-badge-warn">{badges.pendingOrders}</span>
              )}
              {tab.id === 'returns' && badges.pendingReturns > 0 && (
                <span className="admin-nav-badge-warn">{badges.pendingReturns}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="admin-sidebar-footer">
          <span className="admin-sidebar-footer-icon">👤</span>
          <div>
            <div className="admin-sidebar-footer-name">{user?.username}</div>
            <div style={{ fontSize: '0.75rem', color: '#a7f3d0' }}>{isAdmin ? 'Quản trị viên hệ thống' : 'Quản lý'}</div>
          </div>
        </div>
      </aside>

      {/* ─── MAIN CONTENT ───────────────────────────────────────────────── */}
      <main className="admin-main">
        <header className="admin-topbar">
          <h3 className="admin-topbar-title">{TABS.find((t) => t.id === activeTab)?.title}</h3>
          <div className="admin-topbar-actions">
            <button onClick={() => navigate('/')} className="admin-btn-store">
              🏠 Xem cửa hàng
            </button>
            <button onClick={handleLogout} className="admin-btn-danger-outline">
              Đăng xuất
            </button>
          </div>
        </header>

        <div className="admin-content">
          {/* key = tab + params: a card that opens a tab with a starting filter remounts it with that filter */}
          {activeTab === 'dashboard' && <OverviewTab onNavigate={openTab} onBadges={mergeBadges} />}
          {activeTab === 'products' && <ProductsTab key={tabParams.stockFilter || 'all'} initialStockFilter={tabParams.stockFilter} />}
          {activeTab === 'categories' && <CategoriesTab />}
          {activeTab === 'orders' && <OrdersTab key={tabParams.orderStatus || 'all'} initialStatus={tabParams.orderStatus} onBadges={mergeBadges} />}
          {activeTab === 'returns' && <ReturnsTab key={tabParams.returnStatus || 'all'} initialStatus={tabParams.returnStatus} onBadges={mergeBadges} />}
          {activeTab === 'inventory' && <InventoryTab key={tabParams.lowStock ? 'low' : 'all'} initialLowStock={Boolean(tabParams.lowStock)} />}
          {activeTab === 'users' && <UsersTab key={tabParams.userId || 'all'} initialUserId={tabParams.userId} />}
          {activeTab === 'discounts' && <DiscountsTab />}
          {activeTab === 'notifications' && <NotificationsTab />}
        </div>
      </main>
    </div>
  );
};

export default AdminDashboard;
