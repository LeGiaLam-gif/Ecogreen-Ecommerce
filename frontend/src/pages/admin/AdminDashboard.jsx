import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  getAllProductsForAdmin, createProduct, updateProduct, deleteProduct,
} from '../../services/productApi';
import { getAllCategories, createCategory, updateCategory, deleteCategory } from '../../services/categoryApi';
import { getAllOrders, updateOrderStatus } from '../../services/orderApi';
import { getAllUsers, deleteUser } from '../../services/userApi';
import AdminStats from '../../components/AdminStats';
import { resolveProductImage } from '../../utils/imageResolver';

const emptyProduct = { name: '', price: '', image: '', description: '', stockQuantity: 0, categoryId: '', status: 'ACTIVE' };
const emptyCategory = { name: '', description: '' };

/**
 * Admin CMS - products, categories, orders, users.
 * The frontend hiding these tabs behind <ProtectedRoute adminOnly> is a UX
 * convenience only; every write here re-checks the ADMIN role on the backend.
 */
const AdminDashboard = () => {
  const [activeTab, setActiveTab] = useState('products'); // products | categories | orders | users
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [orders, setOrders] = useState([]);
  const [users, setUsers] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');

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
      if (activeTab === 'products') {
        const [p, c] = await Promise.all([getAllProductsForAdmin(), getAllCategories()]);
        setProducts(p);
        setCategories(c);
      } else if (activeTab === 'categories') {
        setCategories(await getAllCategories());
      } else if (activeTab === 'orders') {
        setOrders(await getAllOrders());
      } else if (activeTab === 'users') {
        setUsers(await getAllUsers());
      }
    } catch (error) {
      setErrorMsg(error.friendlyMessage || 'Could not load data.');
    } finally {
      setLoading(false);
    }
  };

  // --- Products ---
  const handleDeleteProduct = async (id) => {
    if (!window.confirm('Deactivate this product?')) return;
    try { await deleteProduct(id); loadData(); } catch (e) { alert(e.friendlyMessage || 'Failed.'); }
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
      alert(err.friendlyMessage || 'Could not save product.');
    }
  };

  // --- Categories ---
  const handleDeleteCategory = async (id) => {
    if (!window.confirm('Delete this category? Products in it will be affected.')) return;
    try { await deleteCategory(id); loadData(); } catch (e) { alert(e.friendlyMessage || 'Failed.'); }
  };

  const handleCategorySubmit = async (e) => {
    e.preventDefault();
    try {
      if (isEditCategory) await updateCategory(currentCategory.id, currentCategory);
      else await createCategory(currentCategory);
      setIsCategoryModalOpen(false);
      loadData();
    } catch (err) {
      alert(err.friendlyMessage || 'Could not save category.');
    }
  };

  // --- Orders ---
  const handleUpdateStatus = async (orderId, newStatus) => {
    try { await updateOrderStatus(orderId, newStatus); loadData(); }
    catch (e) { alert(e.friendlyMessage || 'Could not update status.'); }
  };

  // --- Users ---
  const handleDeleteUser = async (id, name) => {
    if (!window.confirm(`Delete account "${name}"?`)) return;
    try { await deleteUser(id); loadData(); } catch (e) { alert(e.friendlyMessage || 'Failed.'); }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const tabLabel = { products: 'Products', categories: 'Categories', orders: 'Orders', users: 'Users' }[activeTab];

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#f0f2f5' }}>
      {/* SIDEBAR */}
      <div style={{ width: '260px', backgroundColor: '#1b3a1f', color: '#fff', padding: '20px 0', display: 'flex', flexDirection: 'column' }}>
        <div style={{ padding: '0 20px 20px', borderBottom: '1px solid #2e5233', marginBottom: '20px' }}>
          <h2 style={{ fontSize: '1.2rem', margin: 0 }}>🌿 EcoGreen Admin</h2>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {['products', 'categories', 'orders', 'users'].map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                padding: '15px 20px', textAlign: 'left',
                backgroundColor: activeTab === tab ? '#2e5233' : 'transparent',
                color: '#fff', border: 'none', cursor: 'pointer', fontSize: '1rem',
                borderLeft: activeTab === tab ? '4px solid #66bb6a' : '4px solid transparent',
              }}
            >
              {tab === 'products' && '📦 Products'}
              {tab === 'categories' && '🏷️ Categories'}
              {tab === 'orders' && '📜 Orders'}
              {tab === 'users' && '👥 Users'}
            </button>
          ))}
        </div>
        <div style={{ marginTop: 'auto', padding: '20px', borderTop: '1px solid #2e5233', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{ fontSize: '1.2rem' }}>👤</span>
          <span style={{ fontSize: '0.9rem', color: '#c8e6c9' }}>{user?.username} (Admin)</span>
        </div>
      </div>

      {/* MAIN */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div style={{ height: '70px', backgroundColor: '#fff', display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 30px', boxShadow: '0 2px 4px rgba(0,0,0,0.05)' }}>
          <h3 style={{ margin: 0, color: '#333' }}>{tabLabel}</h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
            <button onClick={() => navigate('/')} style={{ padding: '8px 15px', backgroundColor: '#2e7d32', color: '#fff', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold' }}>🏠 Storefront</button>
            <button onClick={handleLogout} style={{ padding: '8px 15px', color: '#c62828', border: '1px solid #c62828', borderRadius: '5px', backgroundColor: 'transparent', cursor: 'pointer', fontWeight: 'bold' }}>Log out</button>
          </div>
        </div>

        <div style={{ padding: '30px', overflowY: 'auto' }}>
          <AdminStats />

          {errorMsg && <p style={{ color: '#c62828' }}>{errorMsg}</p>}

          {loading ? (
            <div style={{ padding: '20px', textAlign: 'center' }}>Loading...</div>
          ) : (
            <>
              {activeTab === 'products' && (
                <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px' }}>
                    <input
                      type="text" placeholder="Search products..." value={searchTerm}
                      onChange={(e) => setSearchTerm(e.target.value)}
                      style={{ padding: '10px', width: '300px', borderRadius: '5px', border: '1px solid #ddd' }}
                    />
                    <button
                      onClick={() => { setIsEditProduct(false); setCurrentProduct(emptyProduct); setIsProductModalOpen(true); }}
                      style={{ padding: '10px 20px', backgroundColor: '#2e7d32', color: '#fff', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold' }}
                    >
                      + Add product
                    </button>
                  </div>
                  <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                      <tr style={{ backgroundColor: '#f8f9fa', textAlign: 'left', borderBottom: '2px solid #eee' }}>
                        <th style={{ padding: '12px' }}>Image</th>
                        <th style={{ padding: '12px' }}>Name</th>
                        <th style={{ padding: '12px' }}>Category</th>
                        <th style={{ padding: '12px' }}>Price</th>
                        <th style={{ padding: '12px' }}>Stock</th>
                        <th style={{ padding: '12px' }}>Status</th>
                        <th style={{ padding: '12px', textAlign: 'center' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {products.filter(p => p.name.toLowerCase().includes(searchTerm.toLowerCase())).map(p => (
                        <tr key={p.id} style={{ borderBottom: '1px solid #f0f0f0' }}>
                          <td style={{ padding: '12px' }}>
                            <img src={resolveProductImage(p.image)} alt="" style={{ width: '40px', height: '40px', objectFit: 'cover', borderRadius: '4px' }} />
                          </td>
                          <td style={{ padding: '12px', fontWeight: '500' }}>{p.name}</td>
                          <td style={{ padding: '12px' }}>{p.categoryName}</td>
                          <td style={{ padding: '12px', color: '#2e7d32' }}>{new Intl.NumberFormat('vi-VN').format(p.price)}₫</td>
                          <td style={{ padding: '12px' }}>
                            <span style={{
                              padding: '4px 8px', borderRadius: '4px',
                              backgroundColor: p.stockQuantity > 10 ? '#d4edda' : p.stockQuantity > 0 ? '#fff3cd' : '#f8d7da',
                              color: p.stockQuantity > 10 ? '#155724' : p.stockQuantity > 0 ? '#856404' : '#721c24',
                              fontWeight: 'bold',
                            }}>
                              {p.stockQuantity}
                            </span>
                          </td>
                          <td style={{ padding: '12px' }}>{p.status}</td>
                          <td style={{ padding: '12px', textAlign: 'center' }}>
                            <button onClick={() => {
                              setIsEditProduct(true);
                              setCurrentProduct({ ...p, categoryId: p.categoryId });
                              setIsProductModalOpen(true);
                            }} style={{ marginRight: '10px', color: '#1976d2', border: 'none', background: 'none', cursor: 'pointer' }}>Edit</button>
                            <button onClick={() => handleDeleteProduct(p.id)} style={{ color: '#c62828', border: 'none', background: 'none', cursor: 'pointer' }}>Deactivate</button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {products.length === 0 && <p style={{ padding: '20px', textAlign: 'center', color: '#888' }}>No products yet. Add your first one above.</p>}
                </div>
              )}

              {activeTab === 'categories' && (
                <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '20px' }}>
                    <button
                      onClick={() => { setIsEditCategory(false); setCurrentCategory(emptyCategory); setIsCategoryModalOpen(true); }}
                      style={{ padding: '10px 20px', backgroundColor: '#2e7d32', color: '#fff', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold' }}
                    >
                      + Add category
                    </button>
                  </div>
                  <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                      <tr style={{ backgroundColor: '#f8f9fa', textAlign: 'left', borderBottom: '2px solid #eee' }}>
                        <th style={{ padding: '12px' }}>Name</th>
                        <th style={{ padding: '12px' }}>Description</th>
                        <th style={{ padding: '12px', textAlign: 'center' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {categories.map(c => (
                        <tr key={c.id} style={{ borderBottom: '1px solid #f0f0f0' }}>
                          <td style={{ padding: '12px', fontWeight: '500' }}>{c.name}</td>
                          <td style={{ padding: '12px', color: '#666' }}>{c.description}</td>
                          <td style={{ padding: '12px', textAlign: 'center' }}>
                            <button onClick={() => { setIsEditCategory(true); setCurrentCategory(c); setIsCategoryModalOpen(true); }} style={{ marginRight: '10px', color: '#1976d2', border: 'none', background: 'none', cursor: 'pointer' }}>Edit</button>
                            <button onClick={() => handleDeleteCategory(c.id)} style={{ color: '#c62828', border: 'none', background: 'none', cursor: 'pointer' }}>Delete</button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {categories.length === 0 && <p style={{ padding: '20px', textAlign: 'center', color: '#888' }}>No categories yet. Add your first one above.</p>}
                </div>
              )}

              {activeTab === 'orders' && (
                <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                      <tr style={{ backgroundColor: '#f8f9fa', textAlign: 'left', borderBottom: '2px solid #eee' }}>
                        <th style={{ padding: '12px' }}>Order</th>
                        <th style={{ padding: '12px' }}>Customer</th>
                        <th style={{ padding: '12px' }}>Date</th>
                        <th style={{ padding: '12px' }}>Total</th>
                        <th style={{ padding: '12px' }}>Status</th>
                        <th style={{ padding: '12px', textAlign: 'center' }}>Update</th>
                      </tr>
                    </thead>
                    <tbody>
                      {orders.map(o => (
                        <tr key={o.id} style={{ borderBottom: '1px solid #f0f0f0' }}>
                          <td style={{ padding: '12px', fontWeight: 'bold' }}>#{o.id}</td>
                          <td style={{ padding: '12px' }}>{o.customerName}</td>
                          <td style={{ padding: '12px' }}>{new Date(o.createdAt).toLocaleString('vi-VN')}</td>
                          <td style={{ padding: '12px', color: '#2e7d32', fontWeight: 'bold' }}>{new Intl.NumberFormat('vi-VN').format(o.totalPrice)}₫</td>
                          <td style={{ padding: '12px' }}>
                            <span style={{ padding: '4px 8px', borderRadius: '4px', fontSize: '0.85rem', backgroundColor: o.status === 'PENDING' ? '#fff3cd' : o.status === 'CANCELLED' ? '#f8d7da' : '#d4edda', color: o.status === 'PENDING' ? '#856404' : o.status === 'CANCELLED' ? '#721c24' : '#155724' }}>
                              {o.status}
                            </span>
                          </td>
                          <td style={{ padding: '12px', textAlign: 'center' }}>
                            <select
                              value={o.status}
                              onChange={(e) => handleUpdateStatus(o.id, e.target.value)}
                              style={{ padding: '5px', borderRadius: '4px', border: '1px solid #ddd' }}
                            >
                              <option value="PENDING">Pending</option>
                              <option value="CONFIRMED">Confirmed</option>
                              <option value="PAID">Paid</option>
                              <option value="CANCELLED">Cancelled</option>
                            </select>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {orders.length === 0 && <p style={{ padding: '20px', textAlign: 'center', color: '#888' }}>No orders yet.</p>}
                </div>
              )}

              {activeTab === 'users' && (
                <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                      <tr style={{ backgroundColor: '#f8f9fa', textAlign: 'left', borderBottom: '2px solid #eee' }}>
                        <th style={{ padding: '12px' }}>ID</th>
                        <th style={{ padding: '12px' }}>Username</th>
                        <th style={{ padding: '12px' }}>Email</th>
                        <th style={{ padding: '12px' }}>Roles</th>
                        <th style={{ padding: '12px', textAlign: 'center' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {users.map(u => (
                        <tr key={u.id} style={{ borderBottom: '1px solid #f0f0f0' }}>
                          <td style={{ padding: '12px' }}>#{u.id}</td>
                          <td style={{ padding: '12px', fontWeight: '500' }}>{u.username}</td>
                          <td style={{ padding: '12px' }}>{u.email}</td>
                          <td style={{ padding: '12px' }}>
                            {u.roles.map(r => (
                              <span key={r} style={{ padding: '4px 8px', borderRadius: '4px', fontSize: '0.85rem', backgroundColor: r === 'ADMIN' ? '#c62828' : '#1976d2', color: '#fff', marginRight: '4px' }}>
                                {r}
                              </span>
                            ))}
                          </td>
                          <td style={{ padding: '12px', textAlign: 'center' }}>
                            {!u.roles.includes('ADMIN') && (
                              <button onClick={() => handleDeleteUser(u.id, u.username)} style={{ color: '#c62828', border: 'none', background: 'none', cursor: 'pointer' }}>Delete</button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {users.length === 0 && <p style={{ padding: '20px', textAlign: 'center', color: '#888' }}>No users yet.</p>}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* PRODUCT MODAL */}
      {isProductModalOpen && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
          <div style={{ backgroundColor: '#fff', padding: '30px', borderRadius: '10px', width: '500px', maxHeight: '90vh', overflowY: 'auto' }}>
            <h3>{isEditProduct ? 'Edit product' : 'Add product'}</h3>
            <form onSubmit={handleProductSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
              <input type="text" placeholder="Name" value={currentProduct.name} onChange={e => setCurrentProduct({ ...currentProduct, name: e.target.value })} required style={{ padding: '10px' }} />
              <select value={currentProduct.categoryId} onChange={e => setCurrentProduct({ ...currentProduct, categoryId: e.target.value })} required style={{ padding: '10px' }}>
                <option value="">Select a category</option>
                {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
              <input type="number" step="0.01" placeholder="Price" value={currentProduct.price} onChange={e => setCurrentProduct({ ...currentProduct, price: e.target.value })} required style={{ padding: '10px' }} />
              <input type="number" placeholder="Stock quantity" value={currentProduct.stockQuantity} onChange={e => setCurrentProduct({ ...currentProduct, stockQuantity: parseInt(e.target.value) || 0 })} required style={{ padding: '10px' }} />
              <input type="text" placeholder="Image filename (in assets/products) or full URL" value={currentProduct.image || ''} onChange={e => setCurrentProduct({ ...currentProduct, image: e.target.value })} style={{ padding: '10px' }} />
              <textarea placeholder="Description" value={currentProduct.description || ''} onChange={e => setCurrentProduct({ ...currentProduct, description: e.target.value })} style={{ padding: '10px' }} />
              {isEditProduct && (
                <select value={currentProduct.status} onChange={e => setCurrentProduct({ ...currentProduct, status: e.target.value })} style={{ padding: '10px' }}>
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              )}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button type="button" onClick={() => setIsProductModalOpen(false)}>Cancel</button>
                <button type="submit" style={{ backgroundColor: '#2e7d32', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '5px' }}>Save</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CATEGORY MODAL */}
      {isCategoryModalOpen && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
          <div style={{ backgroundColor: '#fff', padding: '30px', borderRadius: '10px', width: '450px' }}>
            <h3>{isEditCategory ? 'Edit category' : 'Add category'}</h3>
            <form onSubmit={handleCategorySubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
              <input type="text" placeholder="Name" value={currentCategory.name} onChange={e => setCurrentCategory({ ...currentCategory, name: e.target.value })} required style={{ padding: '10px' }} />
              <textarea placeholder="Description" value={currentCategory.description || ''} onChange={e => setCurrentCategory({ ...currentCategory, description: e.target.value })} style={{ padding: '10px' }} />
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button type="button" onClick={() => setIsCategoryModalOpen(false)}>Cancel</button>
                <button type="submit" style={{ backgroundColor: '#2e7d32', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '5px' }}>Save</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminDashboard;
