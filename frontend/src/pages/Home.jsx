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
        setError(err.friendlyMessage || 'Could not load products right now.');
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
      <header className="home-header">
        <h1 className="home-title">EcoGreen</h1>
        <p className="home-subtitle">Sustainable, everyday products for a greener home.</p>
      </header>

      <BannerSlider />

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
            <p>Loading products...</p>
          </div>
        ) : error ? (
          <div className="no-results">
            <h3>Something went wrong</h3>
            <p>{error}</p>
          </div>
        ) : (
          <>
            <div className="results-info">
              {searchTerm && (
                <p>Found <strong>{filteredProducts.length}</strong> result(s) for "{searchTerm}"</p>
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
                <h3>No products available yet</h3>
                <p>Check back soon - new EcoGreen products are added regularly.</p>
              </div>
            )}

            {displayCount < filteredProducts.length && (
              <div className="load-more-section">
                <button className="load-more-btn" onClick={() => setDisplayCount((c) => c + 12)}>
                  Load more
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
