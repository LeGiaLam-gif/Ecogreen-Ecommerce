import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  getAllProductsForAdmin, createProduct, updateProduct, deleteProduct,
} from '../../services/productApi';
import { getAllCategories, createCategory, updateCategory, deleteCategory } from '../../services/categoryApi';
import { getAllOrders, updateOrderStatus } from '../../services/orderApi';
import { getAllUsers, deleteUser } from '../../services/userApi';
import { getAllReturns, updateReturnStatus } from '../../services/returnApi';
import SalesDashboard from './SalesDashboard';
import { resolveProductImage } from '../../utils/imageResolver';
import './AdminDashboard.css';

const emptyProduct = { name: '', price: '', image: '', description: '', stockQuantity: 0, categoryId: '', status: 'ACTIVE' };
const emptyCategory = { name: '', description: '' };

const STATUS_MAP = {
  PENDING: { label: 'Chờ thanh toán', color: '#856404', bg: '#fff3cd' },
  CONFIRMED: { label: 'Đã xác nhận', color: '#004085', bg: '#cce5ff' },
  PAID: { label: 'Đã thanh toán', color: '#155724', bg: '#d4edda' },
  CANCELLED: { label: 'Đã hủy', color: '#721c24', bg: '#f8d7da' },
};

/**
 * Admin CMS - Quản trị toàn diện hệ sinh thái thương mại điện tử EcoGreen.
 * Logic nghiệp vụ chuẩn: Tổng quan (Dashboard) -> Sản phẩm -> Danh mục -> Đơn hàng -> Đổi trả -> Người dùng.
 */
const AdminDashboard = () => {
  // Tab mặc định khi đăng nhập quản trị luôn là Tổng quan (dashboard)
  const [activeTab, setActiveTab] = useState('dashboard');
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  // Dữ liệu chính
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [orders, setOrders] = useState([]);
  const [users, setUsers] = useState([]);
  const [returns, setReturns] = useState([]);

  // Bộ lọc chuyên sâu cho từng trang
  const [searchTerm, setSearchTerm] = useState('');
  const [orderStatusFilter, setOrderStatusFilter] = useState('ALL');
  const [productCategoryFilter, setProductCategoryFilter] = useState('ALL');
  const [productStockFilter, setProductStockFilter] = useState('ALL');
  const [productStatusFilter, setProductStatusFilter] = useState('ALL');
  const [returnStatusFilter, setReturnStatusFilter] = useState('ALL');
  const [userRoleFilter, setUserRoleFilter] = useState('ALL');

  // Modals
  const [isProductModalOpen, setIsProductModalOpen] = useState(false);
  const [currentProduct, setCurrentProduct] = useState(emptyProduct);
  const [isEditProduct, setIsEditProduct] = useState(false);

  const [isCategoryModalOpen, setIsCategoryModalOpen] = useState(false);
  const [currentCategory, setCurrentCategory] = useState(emptyCategory);
  const [isEditCategory, setIsEditCategory] = useState(false);

  useEffect(() => {
    loadData();
    // eslint-disable-next-line
  }, [activeTab]);

  const loadData = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      if (activeTab === 'dashboard') {
        // Tải đồng thời để tính các chỉ số vận hành và cảnh báo thời gian thực
        const [p, c, o, r, u] = await Promise.all([
          getAllProductsForAdmin(),
          getAllCategories(),
          getAllOrders(),
          getAllReturns(),
          getAllUsers(),
        ]);
        setProducts(p);
        setCategories(c);
        setOrders(o);
        setReturns(r);
        setUsers(u);
      } else if (activeTab === 'products') {
        const [p, c] = await Promise.all([getAllProductsForAdmin(), getAllCategories()]);
        setProducts(p);
        setCategories(c);
      } else if (activeTab === 'categories') {
        setCategories(await getAllCategories());
      } else if (activeTab === 'orders') {
        setOrders(await getAllOrders());
      } else if (activeTab === 'users') {
        setUsers(await getAllUsers());
      } else if (activeTab === 'returns') {
        setReturns(await getAllReturns());
      }
    } catch (error) {
      setErrorMsg(error.friendlyMessage || 'Không thể tải dữ liệu.');
    } finally {
      setLoading(false);
    }
  };

  // --- Products Handler ---
  const handleDeleteProduct = async (id) => {
    if (!window.confirm('Bạn có chắc chắn muốn ngừng kinh doanh sản phẩm này?')) return;
    try {
      await deleteProduct(id);
      loadData();
    } catch (e) {
      alert(e.friendlyMessage || 'Thao tác thất bại.');
    }
  };

  const handleProductSubmit = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        name: currentProduct.name,
        price: Number(currentProduct.price),
        stockQuantity: Number(currentProduct.stockQuantity),
        image: currentProduct.image,
        description: currentProduct.description,
        categoryId: Number(currentProduct.categoryId),
        status: currentProduct.status,
      };
      if (isEditProduct) await updateProduct(currentProduct.id, payload);
      else await createProduct(payload);
      setIsProductModalOpen(false);
      loadData();
    } catch (err) {
      alert(err.friendlyMessage || 'Không thể lưu thông tin sản phẩm.');
    }
  };

  // --- Categories Handler ---
  const handleDeleteCategory = async (id) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa danh mục này? Các sản phẩm thuộc danh mục sẽ bị ảnh hưởng.')) return;
    try {
      await deleteCategory(id);
      loadData();
    } catch (e) {
      alert(e.friendlyMessage || 'Thao tác thất bại.');
    }
  };

  const handleCategorySubmit = async (e) => {
    e.preventDefault();
    try {
      if (isEditCategory) await updateCategory(currentCategory.id, currentCategory);
      else await createCategory(currentCategory);
      setIsCategoryModalOpen(false);
      loadData();
    } catch (err) {
      alert(err.friendlyMessage || 'Không thể lưu thông tin danh mục.');
    }
  };

  // --- Orders Handler ---
  const handleUpdateStatus = async (orderId, newStatus) => {
    try {
      await updateOrderStatus(orderId, newStatus);
      loadData();
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể cập nhật trạng thái đơn hàng.');
    }
  };

  // --- Returns Handler ---
  const handleUpdateReturnStatus = async (id, status) => {
    const defaultNote = status === 'APPROVED'
      ? 'EcoGreen đã chấp thuận yêu cầu đổi trả. Vui lòng đóng gói sản phẩm gửi về địa chỉ shop.'
      : 'Yêu cầu không đáp ứng điều kiện đổi trả trong 7 ngày.';
    const adminNote = prompt(`Nhập ghi chú phản hồi khách hàng:`, defaultNote);
    if (adminNote === null) return;
    try {
      await updateReturnStatus(id, status, adminNote);
      loadData();
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể cập nhật trạng thái đổi/trả.');
    }
  };

  // --- Users Handler ---
  const handleDeleteUser = async (id, name) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa tài khoản "${name}"?`)) return;
    try {
      await deleteUser(id);
      loadData();
    } catch (e) {
      alert(e.friendlyMessage || 'Thao tác thất bại.');
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // Tính toán nhanh các chỉ số vận hành cần xử lý (Action Items)
  const pendingOrders = orders.filter(o => o.status === 'PENDING');
  const pendingReturns = returns.filter(r => r.status === 'PENDING');
  const lowStockProducts = products.filter(p => p.stockQuantity <= 10);
  const recentOrders = [...orders].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 5);

  const tabLabels = {
    dashboard: '📊 Tổng quan hệ thống (Dashboard)',
    products: '📦 Quản lý Sản phẩm & Kho hàng',
    categories: '🏷️ Quản lý Danh mục sản phẩm',
    orders: '📜 Quản lý Đơn hàng',
    returns: '♻️ Quản lý Đổi / Trả hàng (Chính sách 7 ngày)',
    users: '👥 Quản lý Người dùng & Phân quyền',
  };

  // Lọc sản phẩm theo điều kiện
  const filteredProducts = products.filter(p => {
    const matchSearch = p.name.toLowerCase().includes(searchTerm.toLowerCase());
    const matchCategory = productCategoryFilter === 'ALL' || String(p.categoryId) === String(productCategoryFilter);
    const matchStock = productStockFilter === 'ALL'
      ? true
      : productStockFilter === 'LOW_STOCK'
        ? p.stockQuantity <= 10 && p.stockQuantity > 0
        : productStockFilter === 'OUT_OF_STOCK'
          ? p.stockQuantity <= 0
          : p.stockQuantity > 10;
    const matchStatus = productStatusFilter === 'ALL' || p.status === productStatusFilter;
    return matchSearch && matchCategory && matchStock && matchStatus;
  });

  // Lọc đơn hàng theo điều kiện
  const filteredOrders = orders.filter(o => {
    const matchStatus = orderStatusFilter === 'ALL' || o.status === orderStatusFilter;
    const matchSearch = searchTerm === '' ||
      String(o.id).includes(searchTerm) ||
      (o.customerName && o.customerName.toLowerCase().includes(searchTerm.toLowerCase()));
    return matchStatus && matchSearch;
  });

  // Lọc đổi trả theo điều kiện
  const filteredReturns = returns.filter(r => {
    const matchStatus = returnStatusFilter === 'ALL' || r.status === returnStatusFilter;
    const matchSearch = searchTerm === '' ||
      String(r.orderId).includes(searchTerm) ||
      (r.username && r.username.toLowerCase().includes(searchTerm.toLowerCase()));
    return matchStatus && matchSearch;
  });

  // Lọc người dùng theo điều kiện
  const filteredUsers = users.filter(u => {
    const matchRole = userRoleFilter === 'ALL' || (u.roles && u.roles.includes(userRoleFilter));
    const matchSearch = searchTerm === '' ||
      (u.username && u.username.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (u.email && u.email.toLowerCase().includes(searchTerm.toLowerCase()));
    return matchRole && matchSearch;
  });

  return (
    <div className="admin-shell">
      {/* ─── SIDEBAR CHUẨN LOGIC ────────────────────────────────────────── */}
      <aside className="admin-sidebar">
        <div className="admin-sidebar-header">
          <h2>🌿 EcoGreen Admin</h2>
          <small>Hệ thống quản trị bán hàng</small>
        </div>

        <nav className="admin-sidebar-nav">
          {[
            { id: 'dashboard', label: '📊 Tổng quan' },
            { id: 'products', label: '📦 Sản phẩm' },
            { id: 'categories', label: '🏷️ Danh mục' },
            { id: 'orders', label: '📜 Đơn hàng' },
            { id: 'returns', label: '♻️ Đổi / Trả hàng' },
            { id: 'users', label: '👥 Người dùng' },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => {
                setActiveTab(tab.id);
                setSearchTerm('');
              }}
              className={`admin-nav-btn ${activeTab === tab.id ? 'admin-nav-btn-active' : ''}`}
            >
              {tab.label}
              {tab.id === 'orders' && pendingOrders.length > 0 && (
                <span className="admin-nav-badge-warn">{pendingOrders.length}</span>
              )}
              {tab.id === 'returns' && pendingReturns.length > 0 && (
                <span className="admin-nav-badge-warn">{pendingReturns.length}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="admin-sidebar-footer">
          <span className="admin-sidebar-footer-icon">👤</span>
          <div>
            <div className="admin-sidebar-footer-name">{user?.username}</div>
            <div style={{ fontSize: '0.75rem', color: '#a7f3d0' }}>Quản trị viên hệ thống</div>
          </div>
        </div>
      </aside>

      {/* ─── MAIN CONTENT ────────────────────────────────────────────────── */}
      <main className="admin-main">
        {/* Top bar */}
        <header className="admin-topbar">
          <h3 className="admin-topbar-title">{tabLabels[activeTab]}</h3>
          <div className="admin-topbar-actions">
            <button
              onClick={() => navigate('/')}
              className="admin-btn-store"
            >
              🏠 Xem cửa hàng
            </button>
            <button
              onClick={handleLogout}
              className="admin-btn-danger-outline"
            >
              Đăng xuất
            </button>
          </div>
        </header>

        <div className="admin-content">
          {errorMsg && <p className="admin-error-text">{errorMsg}</p>}

          {/* ══════════════════════════════════════════════════════════════════
              TAB 1: TỔNG QUAN HỆ THỐNG (DASHBOARD)
              ══════════════════════════════════════════════════════════════════ */}
          {activeTab === 'dashboard' && (
            <div className="admin-dashboard-view">
              {/* Welcome Card & Real-time Action Center */}
              <div className="admin-action-center">
                <div className="admin-action-header">
                  <div>
                    <h3 style={{ margin: 0, fontSize: '1.2rem', color: '#1b3a1f' }}>
                      👋 Xin chào, {user?.username || 'Quản trị viên'}!
                    </h3>
                    <p style={{ margin: '4px 0 0', fontSize: '0.88rem', color: '#64748b' }}>
                      Dưới đây là tình hình hoạt động kinh doanh và các tác vụ cần chú ý hôm nay.
                    </p>
                  </div>
                  <div className="admin-date-badge">
                    📅 {new Date().toLocaleDateString('vi-VN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                  </div>
                </div>

                {/* Action Alert Cards */}
                <div className="admin-alert-cards-grid">
                  <div
                    className={`admin-alert-card ${pendingOrders.length > 0 ? 'alert-warning' : 'alert-success'}`}
                    onClick={() => {
                      setActiveTab('orders');
                      setOrderStatusFilter('PENDING');
                    }}
                    title="Bấm để xem danh sách đơn hàng chờ duyệt"
                  >
                    <div className="alert-card-icon">⏳</div>
                    <div className="alert-card-content">
                      <div className="alert-card-num">{pendingOrders.length}</div>
                      <div className="alert-card-label">Đơn hàng chờ duyệt</div>
                      <div className="alert-card-desc">
                        {pendingOrders.length > 0 ? 'Cần xác nhận và chuẩn bị hàng' : 'Không có đơn chờ duyệt'}
                      </div>
                    </div>
                    <div className="alert-card-arrow">→</div>
                  </div>

                  <div
                    className={`admin-alert-card ${pendingReturns.length > 0 ? 'alert-danger' : 'alert-success'}`}
                    onClick={() => {
                      setActiveTab('returns');
                      setReturnStatusFilter('PENDING');
                    }}
                    title="Bấm để xem yêu cầu đổi/trả cần xử lý"
                  >
                    <div className="alert-card-icon">♻️</div>
                    <div className="alert-card-content">
                      <div className="alert-card-num">{pendingReturns.length}</div>
                      <div className="alert-card-label">Đổi / Trả chờ duyệt</div>
                      <div className="alert-card-desc">
                        {pendingReturns.length > 0 ? 'Cần phản hồi khách trong 7 ngày' : 'Đã xử lý tất cả yêu cầu'}
                      </div>
                    </div>
                    <div className="alert-card-arrow">→</div>
                  </div>

                  <div
                    className={`admin-alert-card ${lowStockProducts.length > 0 ? 'alert-info' : 'alert-success'}`}
                    onClick={() => {
                      setActiveTab('products');
                      setProductStockFilter('LOW_STOCK');
                    }}
                    title="Bấm để xem sản phẩm sắp hết hàng"
                  >
                    <div className="alert-card-icon">⚠️</div>
                    <div className="alert-card-content">
                      <div className="alert-card-num">{lowStockProducts.length}</div>
                      <div className="alert-card-label">Sản phẩm sắp hết hàng</div>
                      <div className="alert-card-desc">
                        {lowStockProducts.length > 0 ? 'Tồn kho ≤ 10 chiếc, cần nhập thêm' : 'Tồn kho ổn định'}
                      </div>
                    </div>
                    <div className="alert-card-arrow">→</div>
                  </div>
                </div>
              </div>

              {/* Báo cáo doanh số & Biểu đồ phân tích (Sales Dashboard Core) */}
              <div style={{ marginTop: '24px' }}>
                <SalesDashboard />
              </div>

              {/* Đơn hàng mới nhất */}
              <div className="admin-panel" style={{ marginTop: '24px' }}>
                <div className="admin-panel-toolbar">
                  <div>
                    <h4 style={{ margin: 0, color: '#1b3a1f', fontSize: '1.05rem' }}>⏱️ Đơn hàng mới nhất</h4>
                    <small style={{ color: '#64748b' }}>5 đơn hàng vừa được khách hàng đặt gần đây</small>
                  </div>
                  <button
                    onClick={() => {
                      setActiveTab('orders');
                      setOrderStatusFilter('ALL');
                    }}
                    className="admin-link-btn"
                    style={{ fontWeight: 600, color: '#2e7d32' }}
                  >
                    Xem tất cả đơn hàng ({orders.length}) →
                  </button>
                </div>

                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>Mã ĐH</th>
                        <th>Khách hàng</th>
                        <th>Thời gian</th>
                        <th>Tổng tiền</th>
                        <th>Trạng thái</th>
                        <th style={{ textAlign: 'center' }}>Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentOrders.map((o) => {
                        const sInfo = STATUS_MAP[o.status] || { label: o.status, color: '#333', bg: '#f1f5f9' };
                        return (
                          <tr key={o.id}>
                            <td className="admin-cell-strong">#{o.id}</td>
                            <td>{o.customerName || 'Khách vãng lai'}</td>
                            <td className="admin-cell-muted">
                              {new Date(o.createdAt).toLocaleString('vi-VN')}
                            </td>
                            <td className="admin-cell-price">
                              {Number(o.totalPrice).toLocaleString('vi-VN')} ₫
                            </td>
                            <td>
                              <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '0.8rem', fontWeight: 600, backgroundColor: sInfo.bg, color: sInfo.color }}>
                                {sInfo.label}
                              </span>
                            </td>
                            <td style={{ textAlign: 'center' }}>
                              <select
                                value={o.status}
                                onChange={(e) => handleUpdateStatus(o.id, e.target.value)}
                                className="admin-status-select"
                              >
                                <option value="PENDING">Chờ thanh toán</option>
                                <option value="CONFIRMED">Đã xác nhận</option>
                                <option value="PAID">Đã thanh toán</option>
                                <option value="CANCELLED">Đã hủy</option>
                              </select>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                  {recentOrders.length === 0 && (
                    <div className="admin-empty">Chưa có đơn hàng nào phát sinh.</div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              TAB 2: QUẢN LÝ SẢN PHẨM (PRODUCTS)
              ══════════════════════════════════════════════════════════════════ */}
          {activeTab === 'products' && (
            <div className="admin-panel">
              {/* Toolbar bộ lọc chuyên nghiệp */}
              <div className="admin-panel-toolbar">
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', flex: 1 }}>
                  <input
                    type="text"
                    placeholder="Tìm sản phẩm theo tên..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="admin-search-input"
                  />
                  <select
                    value={productCategoryFilter}
                    onChange={(e) => setProductCategoryFilter(e.target.value)}
                    className="admin-filter-select"
                  >
                    <option value="ALL">Tất cả danh mục ({categories.length})</option>
                    {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                  <select
                    value={productStockFilter}
                    onChange={(e) => setProductStockFilter(e.target.value)}
                    className="admin-filter-select"
                  >
                    <option value="ALL">Tất cả tồn kho</option>
                    <option value="LOW_STOCK">⚠️ Sắp hết hàng (≤ 10)</option>
                    <option value="OUT_OF_STOCK">❌ Hết hàng (0)</option>
                    <option value="IN_STOCK">✅ Còn nhiều (&gt; 10)</option>
                  </select>
                  <select
                    value={productStatusFilter}
                    onChange={(e) => setProductStatusFilter(e.target.value)}
                    className="admin-filter-select"
                  >
                    <option value="ALL">Tất cả trạng thái</option>
                    <option value="ACTIVE">Đang kinh doanh</option>
                    <option value="INACTIVE">Ngừng bán</option>
                  </select>
                </div>

                <button
                  onClick={() => {
                    setIsEditProduct(false);
                    setCurrentProduct(emptyProduct);
                    setIsProductModalOpen(true);
                  }}
                  className="admin-btn-primary"
                >
                  + Thêm sản phẩm mới
                </button>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Hình ảnh</th>
                      <th>Tên sản phẩm</th>
                      <th>Danh mục</th>
                      <th>Giá bán</th>
                      <th>Tồn kho</th>
                      <th>Trạng thái</th>
                      <th style={{ textAlign: 'center' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredProducts.map((p) => (
                      <tr key={p.id}>
                        <td>
                          <img
                            src={resolveProductImage(p.image)}
                            alt=""
                            className="admin-thumb"
                          />
                        </td>
                        <td className="admin-cell-strong">{p.name}</td>
                        <td className="admin-cell-muted">{p.categoryName}</td>
                        <td className="admin-cell-price">
                          {Number(p.price).toLocaleString('vi-VN')} ₫
                        </td>
                        <td>
                          <span className={`admin-badge-stock ${p.stockQuantity <= 0 ? 'stock-empty' : p.stockQuantity <= 10 ? 'stock-low' : 'stock-ok'}`}>
                            {p.stockQuantity}
                          </span>
                        </td>
                        <td>
                          <span className={`admin-badge-status ${p.status === 'ACTIVE' ? 'status-active' : 'status-inactive'}`}>
                            {p.status === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}
                          </span>
                        </td>
                        <td style={{ textAlign: 'center' }}>
                          <button
                            onClick={() => {
                              setIsEditProduct(true);
                              setCurrentProduct({ ...p, categoryId: p.categoryId });
                              setIsProductModalOpen(true);
                            }}
                            className="admin-btn-action-edit"
                          >
                            Sửa
                          </button>
                          <button
                            onClick={() => handleDeleteProduct(p.id)}
                            className="admin-btn-action-delete"
                          >
                            Ngừng bán
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {filteredProducts.length === 0 && (
                  <div className="admin-empty">Không tìm thấy sản phẩm nào phù hợp với bộ lọc.</div>
                )}
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              TAB 3: QUẢN LÝ DANH MỤC (CATEGORIES)
              ══════════════════════════════════════════════════════════════════ */}
          {activeTab === 'categories' && (
            <div className="admin-panel">
              <div className="admin-panel-toolbar admin-panel-toolbar-end">
                <button
                  onClick={() => {
                    setIsEditCategory(false);
                    setCurrentCategory(emptyCategory);
                    setIsCategoryModalOpen(true);
                  }}
                  className="admin-btn-primary"
                >
                  + Thêm danh mục mới
                </button>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th style={{ width: '80px' }}>Mã DM</th>
                      <th>Tên danh mục</th>
                      <th>Mô tả sinh thái</th>
                      <th style={{ textAlign: 'center', width: '160px' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {categories.map((c) => (
                      <tr key={c.id}>
                        <td className="admin-cell-strong">#{c.id}</td>
                        <td className="admin-cell-strong" style={{ color: '#1b3a1f' }}>{c.name}</td>
                        <td className="admin-cell-muted">{c.description}</td>
                        <td style={{ textAlign: 'center' }}>
                          <button
                            onClick={() => {
                              setIsEditCategory(true);
                              setCurrentCategory(c);
                              setIsCategoryModalOpen(true);
                            }}
                            className="admin-btn-action-edit"
                          >
                            Sửa
                          </button>
                          <button
                            onClick={() => handleDeleteCategory(c.id)}
                            className="admin-btn-action-delete"
                          >
                            Xóa
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {categories.length === 0 && (
                  <div className="admin-empty">Chưa có danh mục nào. Hãy bấm thêm danh mục mới ở trên.</div>
                )}
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              TAB 4: QUẢN LÝ ĐƠN HÀNG (ORDERS)
              ══════════════════════════════════════════════════════════════════ */}
          {activeTab === 'orders' && (
            <div className="admin-panel">
              {/* Thanh lọc trạng thái nhanh dạng Pills */}
              <div className="admin-order-status-tabs">
                {[
                  { key: 'ALL', label: 'Tất cả đơn', count: orders.length },
                  { key: 'PENDING', label: 'Chờ thanh toán', count: orders.filter(o => o.status === 'PENDING').length },
                  { key: 'CONFIRMED', label: 'Đã xác nhận', count: orders.filter(o => o.status === 'CONFIRMED').length },
                  { key: 'PAID', label: 'Đã thanh toán', count: orders.filter(o => o.status === 'PAID').length },
                  { key: 'CANCELLED', label: 'Đã hủy', count: orders.filter(o => o.status === 'CANCELLED').length },
                ].map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => setOrderStatusFilter(item.key)}
                    className={`admin-status-pill ${orderStatusFilter === item.key ? 'active' : ''}`}
                  >
                    {item.label} <span className="pill-count">({item.count})</span>
                  </button>
                ))}
              </div>

              {/* Tìm kiếm */}
              <div className="admin-panel-toolbar">
                <input
                  type="text"
                  placeholder="Tìm theo mã đơn (#123) hoặc tên khách hàng..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="admin-search-input"
                  style={{ width: '360px' }}
                />
                <div style={{ fontSize: '0.88rem', color: '#64748b' }}>
                  Hiển thị <strong>{filteredOrders.length}</strong> đơn hàng
                </div>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Mã ĐH</th>
                      <th>Khách hàng</th>
                      <th>Ngày đặt</th>
                      <th>Tổng tiền</th>
                      <th>Trạng thái hiện tại</th>
                      <th style={{ textAlign: 'center' }}>Cập nhật trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredOrders.map((o) => {
                      const sInfo = STATUS_MAP[o.status] || { label: o.status, color: '#333', bg: '#f1f5f9' };
                      return (
                        <tr key={o.id}>
                          <td className="admin-cell-strong">#{o.id}</td>
                          <td>
                            <div style={{ fontWeight: 600 }}>{o.customerName || 'Khách hàng'}</div>
                            <small style={{ color: '#64748b' }}>{o.phone || ''}</small>
                          </td>
                          <td className="admin-cell-muted">
                            {new Date(o.createdAt).toLocaleString('vi-VN')}
                          </td>
                          <td className="admin-cell-price">
                            {Number(o.totalPrice).toLocaleString('vi-VN')} ₫
                          </td>
                          <td>
                            <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '0.82rem', fontWeight: 600, backgroundColor: sInfo.bg, color: sInfo.color }}>
                              {sInfo.label}
                            </span>
                          </td>
                          <td style={{ textAlign: 'center' }}>
                            <select
                              value={o.status}
                              onChange={(e) => handleUpdateStatus(o.id, e.target.value)}
                              className="admin-status-select"
                            >
                              <option value="PENDING">Chờ thanh toán (PENDING)</option>
                              <option value="CONFIRMED">Đã xác nhận (CONFIRMED)</option>
                              <option value="PAID">Đã thanh toán (PAID)</option>
                              <option value="CANCELLED">Đã hủy (CANCELLED)</option>
                            </select>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                {filteredOrders.length === 0 && (
                  <div className="admin-empty">Không có đơn hàng nào trong trạng thái đã chọn.</div>
                )}
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              TAB 5: QUẢN LÝ ĐỔI / TRẢ HÀNG (RETURNS)
              ══════════════════════════════════════════════════════════════════ */}
          {activeTab === 'returns' && (
            <div className="admin-panel">
              {/* Lọc trạng thái đổi trả */}
              <div className="admin-order-status-tabs">
                {[
                  { key: 'ALL', label: 'Tất cả yêu cầu', count: returns.length },
                  { key: 'PENDING', label: 'Chờ xử lý', count: returns.filter(r => r.status === 'PENDING').length },
                  { key: 'APPROVED', label: 'Đã duyệt', count: returns.filter(r => r.status === 'APPROVED').length },
                  { key: 'REJECTED', label: 'Từ chối', count: returns.filter(r => r.status === 'REJECTED').length },
                ].map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => setReturnStatusFilter(item.key)}
                    className={`admin-status-pill ${returnStatusFilter === item.key ? 'active' : ''}`}
                  >
                    {item.label} <span className="pill-count">({item.count})</span>
                  </button>
                ))}
              </div>

              <div className="admin-panel-toolbar">
                <input
                  type="text"
                  placeholder="Tìm theo mã đơn hoặc tài khoản khách hàng..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="admin-search-input"
                  style={{ width: '360px' }}
                />
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Mã YC</th>
                      <th>Mã Đơn</th>
                      <th>Khách hàng</th>
                      <th>Lý do</th>
                      <th>Chi tiết mô tả</th>
                      <th>Trạng thái</th>
                      <th>Ghi chú phản hồi</th>
                      <th style={{ textAlign: 'center' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredReturns.map((r) => (
                      <tr key={r.id}>
                        <td className="admin-cell-strong">#{r.id}</td>
                        <td className="admin-cell-strong">#{r.orderId}</td>
                        <td>{r.username}</td>
                        <td style={{ color: '#e65100', fontWeight: 600 }}>{r.reason}</td>
                        <td style={{ maxWidth: '240px', fontSize: '0.88rem', color: '#4b5563' }}>
                          {r.description || '—'}
                        </td>
                        <td>
                          <span style={{
                            display: 'inline-block',
                            padding: '4px 10px',
                            borderRadius: '12px',
                            fontSize: '0.78rem',
                            fontWeight: 'bold',
                            backgroundColor: r.status === 'APPROVED' ? '#e8f5e9' : r.status === 'REJECTED' ? '#ffebee' : '#fff8e1',
                            color: r.status === 'APPROVED' ? '#2e7d32' : r.status === 'REJECTED' ? '#c62828' : '#f57f17'
                          }}>
                            {r.status === 'APPROVED' ? 'Đã duyệt' : r.status === 'REJECTED' ? 'Từ chối' : 'Chờ xử lý'}
                          </span>
                        </td>
                        <td style={{ fontSize: '0.88rem', color: '#555' }}>{r.adminNote || '—'}</td>
                        <td style={{ textAlign: 'center' }}>
                          {r.status === 'PENDING' ? (
                            <div style={{ display: 'flex', gap: '8px', justifyContent: 'center' }}>
                              <button
                                onClick={() => handleUpdateReturnStatus(r.id, 'APPROVED')}
                                className="admin-btn-approve"
                              >
                                Duyệt
                              </button>
                              <button
                                onClick={() => handleUpdateReturnStatus(r.id, 'REJECTED')}
                                className="admin-btn-reject"
                              >
                                Từ chối
                              </button>
                            </div>
                          ) : (
                            <span style={{ color: '#9ca3af', fontSize: '0.85rem' }}>Đã hoàn tất</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {filteredReturns.length === 0 && (
                  <div className="admin-empty">Không có yêu cầu đổi/trả nào phù hợp.</div>
                )}
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════
              TAB 6: QUẢN LÝ NGƯỜI DÙNG (USERS)
              ══════════════════════════════════════════════════════════════════ */}
          {activeTab === 'users' && (
            <div className="admin-panel">
              <div className="admin-panel-toolbar">
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                  <input
                    type="text"
                    placeholder="Tìm theo tên đăng nhập hoặc email..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="admin-search-input"
                  />
                  <select
                    value={userRoleFilter}
                    onChange={(e) => setUserRoleFilter(e.target.value)}
                    className="admin-filter-select"
                  >
                    <option value="ALL">Tất cả vai trò</option>
                    <option value="ADMIN">Quản trị viên (ADMIN)</option>
                    <option value="USER">Khách hàng (USER)</option>
                  </select>
                </div>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Mã ND</th>
                      <th>Tên tài khoản</th>
                      <th>Email</th>
                      <th>Vai trò phân quyền</th>
                      <th style={{ textAlign: 'center' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredUsers.map((u) => (
                      <tr key={u.id}>
                        <td className="admin-cell-strong">#{u.id}</td>
                        <td className="admin-cell-strong">{u.username}</td>
                        <td>{u.email}</td>
                        <td>
                          {u.roles.map((r) => (
                            <span
                              key={r}
                              style={{
                                padding: '4px 8px',
                                borderRadius: '4px',
                                fontSize: '0.8rem',
                                backgroundColor: r === 'ADMIN' ? '#c62828' : '#1976d2',
                                color: '#fff',
                                marginRight: '4px',
                                fontWeight: 600,
                              }}
                            >
                              {r === 'ADMIN' ? 'Quản trị viên' : 'Khách hàng'}
                            </span>
                          ))}
                        </td>
                        <td style={{ textAlign: 'center' }}>
                          {!u.roles.includes('ADMIN') ? (
                            <button
                              onClick={() => handleDeleteUser(u.id, u.username)}
                              className="admin-btn-action-delete"
                            >
                              Xóa
                            </button>
                          ) : (
                            <span style={{ color: '#9ca3af', fontSize: '0.85rem' }}>Bảo vệ</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {filteredUsers.length === 0 && (
                  <div className="admin-empty">Không tìm thấy người dùng nào phù hợp.</div>
                )}
              </div>
            </div>
          )}
        </div>
      </main>

      {/* ─── MODAL THÊM/SỬA SẢN PHẨM ─────────────────────────────────────── */}
      {isProductModalOpen && (
        <div className="admin-modal-overlay">
          <div className="admin-modal-card">
            <h3 style={{ color: '#1b3a1f', marginTop: 0 }}>
              {isEditProduct ? '✏️ Chỉnh sửa sản phẩm' : '➕ Thêm sản phẩm mới'}
            </h3>
            <form onSubmit={handleProductSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
              <div>
                <label className="admin-form-label">Tên sản phẩm *</label>
                <input
                  type="text"
                  placeholder="Ví dụ: Balo Vải Đay Tự Nhiên"
                  value={currentProduct.name}
                  onChange={(e) => setCurrentProduct({ ...currentProduct, name: e.target.value })}
                  required
                  className="admin-form-input"
                />
              </div>

              <div>
                <label className="admin-form-label">Danh mục sản phẩm *</label>
                <select
                  value={currentProduct.categoryId}
                  onChange={(e) => setCurrentProduct({ ...currentProduct, categoryId: e.target.value })}
                  required
                  className="admin-form-select"
                >
                  <option value="">-- Chọn danh mục --</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div>
                  <label className="admin-form-label">Giá bán (₫) *</label>
                  <input
                    type="number"
                    step="1000"
                    placeholder="150000"
                    value={currentProduct.price}
                    onChange={(e) => setCurrentProduct({ ...currentProduct, price: e.target.value })}
                    required
                    className="admin-form-input"
                  />
                </div>
                <div>
                  <label className="admin-form-label">Tồn kho *</label>
                  <input
                    type="number"
                    placeholder="50"
                    value={currentProduct.stockQuantity}
                    onChange={(e) => setCurrentProduct({ ...currentProduct, stockQuantity: parseInt(e.target.value) || 0 })}
                    required
                    className="admin-form-input"
                  />
                </div>
              </div>

              <div>
                <label className="admin-form-label">Hình ảnh (URL hoặc tên file trong assets/products)</label>
                <input
                  type="text"
                  placeholder="https://... hoặc ten-anh.jpg"
                  value={currentProduct.image || ''}
                  onChange={(e) => setCurrentProduct({ ...currentProduct, image: e.target.value })}
                  className="admin-form-input"
                />
              </div>

              <div>
                <label className="admin-form-label">Mô tả sản phẩm</label>
                <textarea
                  placeholder="Mô tả chất liệu tái chế, nguồn gốc xuất xứ, đặc tính sinh thái..."
                  rows={3}
                  value={currentProduct.description || ''}
                  onChange={(e) => setCurrentProduct({ ...currentProduct, description: e.target.value })}
                  className="admin-form-textarea"
                />
              </div>

              {isEditProduct && (
                <div>
                  <label className="admin-form-label">Trạng thái kinh doanh</label>
                  <select
                    value={currentProduct.status}
                    onChange={(e) => setCurrentProduct({ ...currentProduct, status: e.target.value })}
                    className="admin-form-select"
                  >
                    <option value="ACTIVE">Đang kinh doanh (ACTIVE)</option>
                    <option value="INACTIVE">Ngừng kinh doanh (INACTIVE)</option>
                  </select>
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <button
                  type="button"
                  onClick={() => setIsProductModalOpen(false)}
                  className="admin-btn-secondary"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="admin-btn-primary"
                >
                  Lưu sản phẩm
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── MODAL THÊM/SỬA DANH MỤC ─────────────────────────────────────── */}
      {isCategoryModalOpen && (
        <div className="admin-modal-overlay">
          <div className="admin-modal-card" style={{ maxWidth: '460px' }}>
            <h3 style={{ color: '#1b3a1f', marginTop: 0 }}>
              {isEditCategory ? '✏️ Chỉnh sửa danh mục' : '➕ Thêm danh mục mới'}
            </h3>
            <form onSubmit={handleCategorySubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
              <div>
                <label className="admin-form-label">Tên danh mục *</label>
                <input
                  type="text"
                  placeholder="Ví dụ: Thời Trang & Phụ Kiện Xanh"
                  value={currentCategory.name}
                  onChange={(e) => setCurrentCategory({ ...currentCategory, name: e.target.value })}
                  required
                  className="admin-form-input"
                />
              </div>
              <div>
                <label className="admin-form-label">Mô tả danh mục</label>
                <textarea
                  placeholder="Mô tả về nhóm sản phẩm sinh thái trong danh mục này..."
                  rows={3}
                  value={currentCategory.description || ''}
                  onChange={(e) => setCurrentCategory({ ...currentCategory, description: e.target.value })}
                  className="admin-form-textarea"
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <button
                  type="button"
                  onClick={() => setIsCategoryModalOpen(false)}
                  className="admin-btn-secondary"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="admin-btn-primary"
                >
                  Lưu danh mục
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminDashboard;
