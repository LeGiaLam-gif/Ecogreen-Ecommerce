import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { resolveProductImage } from '../utils/imageResolver';
import './Cart.css';

const Cart = () => {
    const { cartItems, removeFromCart, updateQuantity, totalPrice, totalItems } = useCart();
    const navigate = useNavigate();
    const [busyItemId, setBusyItemId] = useState(null);
    const [error, setError] = useState('');

    const handleQuantityChange = async (item, nextQuantity) => {
        if (nextQuantity < 1) return;
        setError('');
        setBusyItemId(item.id);
        try {
            await updateQuantity(item.id, nextQuantity);
        } catch (err) {
            setError(err.friendlyMessage || 'Không thể cập nhật số lượng sản phẩm.');
        } finally {
            setBusyItemId(null);
        }
    };

    const handleRemove = async (item) => {
        setError('');
        setBusyItemId(item.id);
        try {
            await removeFromCart(item.id);
        } catch (err) {
            setError(err.friendlyMessage || 'Không thể xóa sản phẩm khỏi giỏ hàng.');
        } finally {
            setBusyItemId(null);
        }
    };

    if (cartItems.length === 0) {
        return (
            <div className="cart-empty-container">
                <div className="cart-empty-icon">🛍️</div>
                <h2>Giỏ hàng của bạn đang trống</h2>
                <p>Hãy khám phá các sản phẩm sinh thái xanh và thêm món đồ bạn yêu thích vào giỏ nhé!</p>
                <button className="cart-back-home" onClick={() => navigate('/')}>
                    Bắt đầu mua sắm ngay
                </button>
            </div>
        );
    }

    return (
        <div className="cart-page-wrapper">
            <div className="cart-container">
                <header className="cart-header">
                    <h1 className="cart-title">Giỏ hàng của bạn</h1>
                    <span className="cart-count">{totalItems} sản phẩm</span>
                </header>

                {error && <p style={{ color: '#c62828', textAlign: 'center' }}>{error}</p>}

                <div className="cart-layout">
                    <div className="cart-items-list">
                        {cartItems.map((item) => (
                            <div key={item.id} className="cart-item-card">
                                <div className="cart-item-image">
                                    <img
                                        src={resolveProductImage(item.product.image)}
                                        alt={item.product.name}
                                    />
                                </div>
                                <div className="cart-item-info">
                                    <h3 className="cart-item-name">{item.product.name}</h3>
                                    <p className="cart-item-price">{Number(item.product.price).toLocaleString('vi-VN')} ₫</p>

                                    <div className="cart-item-actions">
                                        <div className="quantity-controls">
                                            <button
                                                disabled={busyItemId === item.id}
                                                onClick={() => handleQuantityChange(item, item.quantity - 1)}>−</button>
                                            <span>{item.quantity}</span>
                                            <button
                                                disabled={busyItemId === item.id || item.quantity >= item.product.stockQuantity}
                                                onClick={() => handleQuantityChange(item, item.quantity + 1)}>+</button>
                                        </div>
                                        <button
                                            className="remove-btn"
                                            disabled={busyItemId === item.id}
                                            onClick={() => handleRemove(item)}>
                                            Xóa
                                        </button>
                                    </div>
                                </div>
                                <div className="cart-item-total">
                                    {Number(item.subtotal).toLocaleString('vi-VN')} ₫
                                </div>
                            </div>
                        ))}
                    </div>

                    <div className="cart-summary">
                        <div className="summary-card glass">
                            <h3>Tóm tắt đơn hàng</h3>
                            <div className="summary-row">
                                <span>Tạm tính</span>
                                <span>{Number(totalPrice).toLocaleString('vi-VN')} ₫</span>
                            </div>
                            <div className="summary-row">
                                <span>Phí vận chuyển xanh</span>
                                <span className="free-shipping">Miễn phí 🌿</span>
                            </div>
                            <div className="summary-divider"></div>
                            <div className="summary-row total">
                                <span>Tổng thanh toán</span>
                                <span className="total-price">{Number(totalPrice).toLocaleString('vi-VN')} ₫</span>
                            </div>
                            <button
                                className="checkout-btn"
                                onClick={() => navigate('/checkout')}
                            >
                                TIẾN HÀNH ĐẶT HÀNG
                            </button>
                            <Link to="/" className="continue-shopping">← Tiếp tục mua sắm</Link>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Cart;
