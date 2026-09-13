import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { getProductById } from '../services/productApi';
import { resolveProductImage } from '../utils/imageResolver';
import './ProductDetail.css';

const ProductDetail = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();
    const { addToCart } = useCart();
    const [product, setProduct] = useState(null);
    const [loading, setLoading] = useState(true);
    const [quantity, setQuantity] = useState(1);
    const [actionError, setActionError] = useState('');

    useEffect(() => {
        const fetchProduct = async () => {
            try {
                setLoading(true);
                const data = await getProductById(id);
                setProduct(data);
                setQuantity(1);
            } catch (error) {
                setProduct(null);
            } finally {
                setLoading(false);
            }
        };
        fetchProduct();
        window.scrollTo(0, 0);
    }, [id]);

    const outOfStock = product && product.stockQuantity <= 0;

    const requireLogin = () => {
        if (!user) {
            navigate('/login');
            return true;
        }
        return false;
    };

    const handleAddToCart = async () => {
        if (requireLogin()) return;
        setActionError('');
        try {
            await addToCart(product.id, quantity);
        } catch (err) {
            setActionError(err.friendlyMessage || 'Could not add this product to the cart.');
        }
    };

    const handleBuyNow = async () => {
        if (requireLogin()) return;
        setActionError('');
        try {
            await addToCart(product.id, quantity);
            navigate('/cart');
        } catch (err) {
            setActionError(err.friendlyMessage || 'Could not add this product to the cart.');
        }
    };

    if (loading) return (
      <div className="pd-loading">
        <div className="spinner"></div>
      </div>
    );

    if (!product) return (
        <div className="pd-error-container">
            <div className="pd-error-icon">⚠️</div>
            <h2>Product not found</h2>
            <p>It may have been removed, or the link is incorrect.</p>
            <button className="pd-error-btn" onClick={() => navigate('/')}>Back to home</button>
        </div>
    );

    return (
        <div className="pd-wrapper">
          <div className="pd-container">
              <nav className="pd-breadcrumb">
                  <button className="pd-back-link" onClick={() => navigate(-1)}>
                     &#8592; Back
                  </button>
                  <span className="breadcrumb-divider">/</span>
                  <span className="breadcrumb-current">{product.name}</span>
              </nav>

              <div className="pd-layout">
                  <div className="pd-media">
                      <div className="pd-image-box">
                          <img
                             src={resolveProductImage(product.image)}
                             alt={product.name}
                             className="pd-main-img"
                          />
                      </div>
                  </div>

                  <div className="pd-info">
                      <div className="pd-header-info">
                          {product.categoryName && <span className="pd-category-tag">{product.categoryName}</span>}
                          <h1 className="pd-name">{product.name}</h1>
                          <div className="pd-price-badge">
                              <span className="pd-price-label">Price:</span>
                              <span className="pd-current-price">{Number(product.price).toLocaleString()} ₫</span>
                          </div>
                          <p className={outOfStock ? 'pd-stock-out' : 'pd-stock-in'}>
                              {outOfStock ? 'Out of stock' : `In stock: ${product.stockQuantity}`}
                          </p>
                      </div>

                      <div className="pd-section">
                          <h3 className="section-title">Description</h3>
                          <p className="pd-desc-text">{product.description || 'No description provided.'}</p>
                      </div>

                      {!outOfStock && (
                          <div className="pd-quantity-row">
                              <label htmlFor="qty">Quantity</label>
                              <div className="pd-quantity-control">
                                  <button type="button" onClick={() => setQuantity((q) => Math.max(1, q - 1))}>-</button>
                                  <input
                                      id="qty"
                                      type="number"
                                      min="1"
                                      max={product.stockQuantity}
                                      value={quantity}
                                      onChange={(e) => {
                                          const v = Math.max(1, Math.min(product.stockQuantity, Number(e.target.value) || 1));
                                          setQuantity(v);
                                      }}
                                  />
                                  <button type="button" onClick={() => setQuantity((q) => Math.min(product.stockQuantity, q + 1))}>+</button>
                              </div>
                          </div>
                      )}

                      {actionError && <p className="pd-action-error">{actionError}</p>}

                      <div className="pd-cta">
                          <button className="btn-buy-now" onClick={handleBuyNow} disabled={outOfStock}>
                              <span className="btn-label">BUY NOW</span>
                          </button>
                          <button className="btn-add-to-cart-outline" onClick={handleAddToCart} disabled={outOfStock}>
                              <span>Add to cart</span>
                          </button>
                      </div>
                  </div>
              </div>
          </div>
        </div>
    );
};

export default ProductDetail;
