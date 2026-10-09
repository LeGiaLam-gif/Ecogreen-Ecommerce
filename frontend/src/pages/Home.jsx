import React, { useState, useEffect, useRef } from 'react';
import ProductCard from '../components/ProductCard';
import BannerSlider from '../components/BannerSlider';
import CategoryFilter from '../components/CategoryFilter';
import { getProducts } from '../services/productApi';
import { getAllCategories } from '../services/categoryApi';
import './Home.css';

const PAGE_SIZE = 12;
const SEARCH_DEBOUNCE_MS = 300;

const isCancelled = (signal) => signal.aborted;

const Home = ({ searchTerm }) => {
  // list.key records which query (search text + category) the items belong to; while it differs from the query
  // currently wanted, the first page is still loading (no setState is needed at the start of the effect).
  const [list, setList] = useState({ key: null, items: [], meta: null, error: '' });
  const [categories, setCategories] = useState([]);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);
  const [debouncedTerm, setDebouncedTerm] = useState((searchTerm || '').trim());
  const moreController = useRef(null);

  const queryKey = `${debouncedTerm}|${selectedCategoryId ?? ''}`;
  const loading = list.key !== queryKey;
  const products = list.items;
  const meta = list.meta;
  const error = loading ? '' : list.error;

  // Search is done by the server; wait for the user to stop typing before asking.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedTerm((searchTerm || '').trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  useEffect(() => {
    let active = true;
    getAllCategories()
      .then((data) => active && setCategories(data || []))
      .catch(() => {}); // the filter bar is optional: the product list still works without it
    return () => {
      active = false;
    };
  }, []);

  // First page: runs on mount and whenever the search text or category changes. A newer request cancels the older one.
  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    getProducts(
      { keyword: debouncedTerm || undefined, categoryId: selectedCategoryId ?? undefined, page: 0, size: PAGE_SIZE },
      { signal },
    )
      .then((result) => {
        if (isCancelled(signal)) return;
        setList({ key: queryKey, items: result.items || [], meta: result.meta || null, error: '' });
      })
      .catch((err) => {
        if (isCancelled(signal)) return;
        setList({
          key: queryKey,
          items: [],
          meta: null,
          error: err.friendlyMessage || 'Không thể tải danh sách sản phẩm lúc này. Vui lòng thử lại sau.',
        });
      });
    return () => {
      controller.abort();
      if (moreController.current) moreController.current.abort();
    };
  }, [debouncedTerm, selectedCategoryId, queryKey]);

  const hasMore = !loading && meta !== null && meta.page + 1 < meta.totalPages;

  // "Xem thêm": fetch the next page with the same filters and append it (ids already shown are skipped).
  const loadMore = () => {
    if (!hasMore || loadingMore) return;
    const controller = new AbortController();
    moreController.current = controller;
    const { signal } = controller;
    setLoadingMore(true);
    getProducts(
      { keyword: debouncedTerm || undefined, categoryId: selectedCategoryId ?? undefined, page: meta.page + 1, size: PAGE_SIZE },
      { signal },
    )
      .then((result) => {
        if (isCancelled(signal)) return;
        setList((current) => {
          const seen = new Set(current.items.map((p) => p.id));
          return {
            ...current,
            items: [...current.items, ...(result.items || []).filter((p) => !seen.has(p.id))],
            meta: result.meta || null,
          };
        });
      })
      .catch((err) => {
        if (!isCancelled(signal)) setList((current) => ({ ...current, error: err.friendlyMessage || 'Không thể tải thêm sản phẩm.' }));
      })
      .finally(() => {
        if (!isCancelled(signal)) setLoadingMore(false);
      });
  };

  const productsToDisplay = products;
  const totalFound = meta ? meta.totalElements : products.length;

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
          onSelectCategory={(id) => setSelectedCategoryId(id)}
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
              {debouncedTerm && (
                <p>Đã tìm thấy <strong>{totalFound}</strong> sản phẩm cho "{debouncedTerm}"</p>
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

            {hasMore && (
              <div className="load-more-section">
                <button className="load-more-btn" onClick={loadMore} disabled={loadingMore}>
                  {loadingMore ? 'Đang tải...' : 'Xem thêm sản phẩm'}
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
