import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import * as cartApi from '../services/cartApi';
import { useAuth } from './AuthContext';

const CartContext = createContext();

export const useCart = () => {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within a CartProvider');
  return ctx;
};

/**
 * The cart is always the server-side (database) cart for the authenticated
 * user - this is what makes the same cart show up on every device. When no
 * one is logged in, cartItems is simply empty; guest browsing does not
 * fabricate a fake local cart for checkout purposes.
 */
export const CartProvider = ({ children }) => {
  const { user } = useAuth();
  const [cartItems, setCartItems] = useState([]);
  const [totalPrice, setTotalPrice] = useState(0);
  const [loading, setLoading] = useState(false);

  const applyCart = (cart) => {
    setCartItems(cart?.items || []);
    setTotalPrice(cart?.totalPrice || 0);
  };

  const refreshCart = useCallback(async () => {
    if (!user) {
      setCartItems([]);
      setTotalPrice(0);
      return;
    }
    setLoading(true);
    try {
      const cart = await cartApi.getCart();
      applyCart(cart);
    } catch {
      // Not fatal - an empty cart is a valid, expected state.
      setCartItems([]);
      setTotalPrice(0);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    refreshCart();
  }, [refreshCart]);

  const addToCart = async (productId, quantity = 1) => {
    const cart = await cartApi.addToCart(productId, quantity);
    applyCart(cart);
  };

  const updateQuantity = async (cartItemId, quantity) => {
    const cart = await cartApi.updateCartItem(cartItemId, quantity);
    applyCart(cart);
  };

  const removeFromCart = async (cartItemId) => {
    const cart = await cartApi.removeCartItem(cartItemId);
    applyCart(cart);
  };

  const clearCart = async () => {
    await cartApi.clearCart();
    setCartItems([]);
    setTotalPrice(0);
  };

  const totalItems = cartItems.reduce((sum, item) => sum + item.quantity, 0);

  return (
    <CartContext.Provider
      value={{ cartItems, totalItems, totalPrice, loading, addToCart, updateQuantity, removeFromCart, clearCart, refreshCart }}
    >
      {children}
    </CartContext.Provider>
  );
};
