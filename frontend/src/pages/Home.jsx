import React, { useState, useEffect } from 'react';
import ProductCard from '../components/ProductCard';
import BannerSlider from '../components/BannerSlider';
import CategoryFilter from '../components/CategoryFilter';
import { getAllProducts } from '../services/productApi';
import { getAllCategories } from '../services/categoryApi';
import './Home.css';

const Home = ({ searchTerm }) => {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [displayCount, setDisplayCount] = useState(12);
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError('');
      try {
        const [productsData, categoriesData] = await Promise.all([
          getAllProducts(),
          getAllCategories(),
        ]);
        setProducts(productsData || []);
        setCategories(categoriesData || []);
      } catch (err) {
        setError(err.friendlyMessage || 'Không thể tải danh sách sản phẩm lúc này. Vui lòng thử lại sau.');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  // Products always come from the database - there is no hard-coded catalog here.
  const filteredProducts = products.filter((product) => {
    const matchesSearch = product?.name?.toLowerCase().includes((searchTerm || '').toLowerCase());
    const matchesCategory = selectedCategoryId === null || product.categoryId === selectedCategoryId;
    return matchesSearch && matchesCategory;
  });

  const productsToDisplay = filteredProducts.slice(0, displayCount);

  return (
    <div className="home-container">

      <BannerSlider />

      {/* === Section Cam Kết EcoGreen === */}
      <section className="eco-commit">
        <div className="eco-commit-item">
          <span className="eco-commit-icon">🌿</span>
          <div>
            <strong>Đóng gói giấy tái chế 100%</strong>
            <p>Không hộp xốp, không túi nilon</p>
          </div>
        </div>
        <div className="eco-commit-divider" />
        <div className="eco-commit-item">
          <span className="eco-commit-icon">🚚</span>
          <div>
            <strong>Giao hàng không rác thải nhựa</strong>
            <p>Miễn phí giao hàng đơn từ 500K</p>
          </div>
        </div>
        <div className="eco-commit-divider" />
        <div className="eco-commit-item">
          <span className="eco-commit-icon">♻️</span>
          <div>
            <strong>Đổi trả trong 7 ngày</strong>
            <p>Hàng lỗi đổi mới, hoàn tiền 100%</p>
          </div>
        </div>
        <div className="eco-commit-divider" />
        <div className="eco-commit-item">
          <span className="eco-commit-icon">⭐</span>
          <div>
            <strong>Sản phẩm chứng nhận</strong>
            <p>Tái chế được kiểm định chất lượng</p>
          </div>
        </div>
      </section>

      {categories.length > 0 && (
        <CategoryFilter
          categories={categories}
          selectedCategoryId={selectedCategoryId}
          onSelectCategory={(id) => {
            setSelectedCategoryId(id);
            setDisplayCount(12);
          }}
        />
      )}

      <div className="home-content">
        {loading ? (
          <div className="loading-state">
            <div className="spinner"></div>
            <p>Đang tải danh sách sản phẩm sinh thái...</p>
          </div>
        ) : error ? (
          <div className="no-results">
            <h3>Đã xảy ra lỗi khi tải sản phẩm</h3>
            <p>{error}</p>
          </div>
        ) : (
          <>
            <div className="results-info">
              {searchTerm && (
                <p>Đã tìm thấy <strong>{filteredProducts.length}</strong> sản phẩm cho "{searchTerm}"</p>
              )}
            </div>

            {productsToDisplay.length > 0 ? (
              <div className="product-grid">
                {productsToDisplay.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
            ) : (
              <div className="no-results">
                <div className="no-results-icon">🌱</div>
                <h3>Chưa có sản phẩm nào phù hợp</h3>
                <p>Vui lòng thử tìm kiếm với từ khóa khác hoặc quay lại sau.</p>
              </div>
            )}

            {displayCount < filteredProducts.length && (
              <div className="load-more-section">
                <button className="load-more-btn" onClick={() => setDisplayCount((c) => c + 12)}>
                  Xem thêm sản phẩm
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default Home;
