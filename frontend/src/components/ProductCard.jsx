import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { resolveProductImage } from '../utils/imageResolver';
import './ProductCard.css';

const getBadge = (product) => {
  if (product.stockQuantity <= 0) return null;
  if (product.id % 3 === 1) return { text: '🌿 Bán chạy', className: 'badge-bestseller' };
  if (product.id % 3 === 2) return { text: '✨ Mới về', className: 'badge-new' };
  return { text: '♻️ 100% Tái chế', className: 'badge-recycled' };
};

const ProductCard = ({ product }) => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { addToCart } = useCart();
  const outOfStock = product.stockQuantity <= 0;
  const badge = getBadge(product);

  const handleAddToCart = async (e) => {
    e.stopPropagation();
    if (!user) {
      navigate('/login');
      return;
    }
    if (outOfStock) return;
    try {
      await addToCart(product.id, 1);
    } catch (err) {
      alert(err.friendlyMessage || 'Không thể thêm sản phẩm vào giỏ hàng.');
    }
  };

  const handleViewDetail = (e) => {
    e.stopPropagation();
    navigate(`/product/${product.id}`);
  };

  return (
    <div className="product-card" onClick={handleViewDetail}>
      <div className="product-image-container">
        {badge && <span className={`product-badge ${badge.className}`}>{badge.text}</span>}
        <img
          src={resolveProductImage(product.image)}
          alt={product.name}
          className="product-image"
          loading="lazy"
        />
        {outOfStock && <span className="product-badge-outofstock">Hết hàng</span>}
      </div>

      <div className="product-info">
        {product.categoryName && <span className="product-category">{product.categoryName}</span>}
        <h3 className="product-name">{product.name}</h3>
        <p className="product-price">{Number(product.price).toLocaleString()} ₫</p>
      </div>

      <div className="product-actions">
        <button className="btn-add-cart" onClick={handleAddToCart} disabled={outOfStock}>
          <span>{outOfStock ? 'Hết hàng' : '+ Thêm giỏ hàng'}</span>
        </button>
        <button className="btn-detail" onClick={handleViewDetail}>
          <span>Xem chi tiết</span>
        </button>
      </div>
    </div>
  );
};

export default ProductCard;
