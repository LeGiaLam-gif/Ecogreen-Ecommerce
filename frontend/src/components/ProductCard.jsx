import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { resolveProductImage } from '../utils/imageResolver';
import './ProductCard.css';

const ProductCard = ({ product }) => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { addToCart } = useCart();
  const outOfStock = product.stockQuantity <= 0;

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
      alert(err.friendlyMessage || 'Could not add this product to the cart.');
    }
  };

  const handleViewDetail = (e) => {
    e.stopPropagation();
    navigate(`/product/${product.id}`);
  };

  return (
    <div className="product-card" onClick={handleViewDetail}>
      <div className="product-image-container">
        <img
          src={resolveProductImage(product.image)}
          alt={product.name}
          className="product-image"
          loading="lazy"
        />
        {outOfStock && <span className="product-badge-outofstock">Out of stock</span>}
      </div>

      <div className="product-info">
        {product.categoryName && <span className="product-category">{product.categoryName}</span>}
        <h3 className="product-name">{product.name}</h3>
        <p className="product-price">{Number(product.price).toLocaleString()} ₫</p>
      </div>

      <div className="product-actions">
        <button className="btn-add-cart" onClick={handleAddToCart} disabled={outOfStock}>
          <span>Add to cart</span>
        </button>
        <button className="btn-detail" onClick={handleViewDetail}>
          <span>Details</span>
        </button>
      </div>
    </div>
  );
};

export default ProductCard;
