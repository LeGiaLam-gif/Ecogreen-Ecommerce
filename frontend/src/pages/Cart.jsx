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
            setError(err.friendlyMessage || 'Could not update quantity.');
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
            setError(err.friendlyMessage || 'Could not remove this item.');
        } finally {
            setBusyItemId(null);
        }
    };

    if (cartItems.length === 0) {
        return (
            <div className="cart-empty-container">
                <div className="cart-empty-icon">🛍️</div>
                <h2>Your cart is empty</h2>
                <p>Browse our eco-friendly products and add something you like!</p>
                <button className="cart-back-home" onClick={() => navigate('/')}>
                    Start shopping
                </button>
            </div>
        );
    }

    return (
        <div className="cart-page-wrapper">
            <div className="cart-container">
                <header className="cart-header">
                    <h1 className="cart-title">Your cart</h1>
                    <span className="cart-count">{totalItems} item(s)</span>
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
                                    <p className="cart-item-price">{Number(item.product.price).toLocaleString()} ₫</p>

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
                                            Remove
                                        </button>
                                    </div>
                                </div>
                                <div className="cart-item-total">
                                    {Number(item.subtotal).toLocaleString()} ₫
                                </div>
                            </div>
                        ))}
                    </div>

                    <div className="cart-summary">
                        <div className="summary-card glass">
                            <h3>Order summary</h3>
                            <div className="summary-row">
                                <span>Subtotal</span>
                                <span>{Number(totalPrice).toLocaleString()} ₫</span>
                            </div>
                            <div className="summary-row">
                                <span>Shipping</span>
                                <span className="free-shipping">Free</span>
                            </div>
                            <div className="summary-divider"></div>
                            <div className="summary-row total">
                                <span>Total</span>
                                <span className="total-price">{Number(totalPrice).toLocaleString()} ₫</span>
                            </div>
                            <button
                                className="checkout-btn"
                                onClick={() => navigate('/checkout')}
                            >
                                PROCEED TO CHECKOUT
                            </button>
                            <Link to="/" className="continue-shopping">← Continue shopping</Link>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Cart;
