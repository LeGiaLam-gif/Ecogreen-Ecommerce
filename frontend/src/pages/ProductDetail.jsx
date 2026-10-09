import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { getProductByIdOrSlug } from '../services/productApi';
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
    const [activeImage, setActiveImage] = useState(0);

    useEffect(() => {
        const fetchProduct = async () => {
            try {
                setLoading(true);
                const data = await getProductByIdOrSlug(id);
                setProduct(data);
                setQuantity(1);
                setActiveImage(0);
            } catch {
                setProduct(null);
            } finally {
                setLoading(false);
            }
        };
        fetchProduct();
        window.scrollTo(0, 0);
    }, [id]);

    const outOfStock = product && product.stockQuantity <= 0;

    // Gallery from product.images; products without gallery rows fall back to the legacy single image field.
    const gallery = product
        ? (product.images && product.images.length > 0
            ? product.images
            : (product.image ? [{ id: 'legacy', url: product.image, altText: null }] : []))
        : [];
    const shown = gallery[Math.min(activeImage, Math.max(gallery.length - 1, 0))];
    // Strike-through only when the API sends a compare price that is higher than the price.
    const hasComparePrice = product && product.comparePrice != null && Number(product.comparePrice) > Number(product.price);

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
            setActionError(err.friendlyMessage || 'Không thể thêm sản phẩm này vào giỏ hàng.');
        }
    };

    const handleBuyNow = async () => {
        if (requireLogin()) return;
        setActionError('');
        try {
            await addToCart(product.id, quantity);
            navigate('/cart');
        } catch (err) {
            setActionError(err.friendlyMessage || 'Không thể thêm sản phẩm này vào giỏ hàng.');
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
            <h2>Không tìm thấy sản phẩm</h2>
            <p>Sản phẩm có thể đã ngừng bán hoặc đường dẫn không chính xác.</p>
            <button className="pd-error-btn" onClick={() => navigate('/')}>Quay lại trang chủ</button>
        </div>
    );

    return (
        <div className="pd-wrapper">
          <div className="pd-container">
              <nav className="pd-breadcrumb">
                  <button className="pd-back-link" onClick={() => navigate(-1)}>
                     &#8592; Quay lại
                  </button>
                  <span className="breadcrumb-divider">/</span>
                  <span className="breadcrumb-current">{product.name}</span>
              </nav>

              <div className="pd-layout">
                  <div className="pd-media">
                      <div className="pd-image-box">
                          <img
                             src={resolveProductImage(shown ? shown.url : product.image)}
                             alt={(shown && shown.altText) || product.name}
                             className="pd-main-img"
                          />
                      </div>
                      {gallery.length > 1 && (
                          <div className="pd-thumbs">
                              {gallery.map((img, index) => (
                                  <button
                                      key={img.id}
                                      type="button"
                                      className={`pd-thumb ${index === activeImage ? 'pd-thumb-active' : ''}`}
                                      onClick={() => setActiveImage(index)}
                                  >
                                      <img src={resolveProductImage(img.url)} alt={img.altText || product.name} />
                                  </button>
                              ))}
                          </div>
                      )}
                  </div>

                  <div className="pd-info">
                      <div className="pd-header-info">
                          {product.categoryName && <span className="pd-category-tag">{product.categoryName}</span>}
                          <h1 className="pd-name">{product.name}</h1>
                          <div className="pd-price-badge">
                              <span className="pd-price-label">Giá:</span>
                              <span className="pd-current-price">{Number(product.price).toLocaleString()} ₫</span>
                              {hasComparePrice && (
                                  <span className="pd-compare-price">{Number(product.comparePrice).toLocaleString()} ₫</span>
                              )}
                          </div>
                          <p className={outOfStock ? 'pd-stock-out' : 'pd-stock-in'}>
                              {outOfStock ? 'Hết hàng' : `Còn hàng: ${product.stockQuantity} sản phẩm`}
                          </p>
                      </div>

                      <div className="pd-section">
                          <h3 className="section-title">Mô tả sản phẩm</h3>
                          <p className="pd-desc-text">{product.description || 'Chưa có thông tin mô tả chi tiết cho sản phẩm này.'}</p>
                      </div>

                      {!outOfStock && (
                          <div className="pd-quantity-row">
                              <label htmlFor="qty">Số lượng:</label>
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
                              <span className="btn-label">MUA NGAY</span>
                          </button>
                          <button className="btn-add-to-cart-outline" onClick={handleAddToCart} disabled={outOfStock}>
                              <span>Thêm vào giỏ hàng</span>
                          </button>
                      </div>
                  </div>
              </div>
          </div>
        </div>
    );
};

export default ProductDetail;
