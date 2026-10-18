import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import * as cartApi from '../services/cartApi';
import { useAuth } from './AuthContext';

const CartContext = createContext();

export const useCart = () => {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within a CartProvider');
  return ctx;
};

const EMPTY_CART = { items: [], subtotal: 0, totalItems: 0 };

/**
 * The cart is always the server-side (database) cart for the authenticated
 * user - this is what makes the same cart show up on every device. When no
 * one is logged in, the cart is simply empty; guest browsing does not
 * fabricate a fake local cart for checkout purposes.
 *
 * The cart is fetched ONCE per signed-in user (this provider is the only place that does it). Every change answers with
 * the whole updated cart, which replaces the state, so nothing is fetched again. `subtotal` and `totalItems` come from the
 * server and are only displayed; nothing here adds up prices.
 */
export const CartProvider = ({ children }) => {
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const [cart, setCart] = useState(EMPTY_CART);
  const [loading, setLoading] = useState(false);

  const applyCart = useCallback((data) => {
    setCart({
      items: data?.items || [],
      subtotal: data?.subtotal || 0,
      totalItems: data?.totalItems || 0,
    });
  }, []);

  const refreshCart = useCallback(async () => {
    if (!userId) {
      setCart(EMPTY_CART);
      return;
    }
    setLoading(true);
    try {
      applyCart(await cartApi.getCart());
    } catch {
      // Not fatal - an empty cart is a valid, expected state.
      setCart(EMPTY_CART);
    } finally {
      setLoading(false);
    }
  }, [userId, applyCart]);

  useEffect(() => {
    refreshCart();
  }, [refreshCart]);

  const addToCart = async (productId, quantity = 1) => {
    applyCart(await cartApi.addToCart(productId, quantity));
  };

  const updateQuantity = async (cartItemId, quantity) => {
    applyCart(await cartApi.updateCartItem(cartItemId, quantity));
  };

  const removeFromCart = async (cartItemId) => {
    applyCart(await cartApi.removeCartItem(cartItemId));
  };

  const clearCart = async () => {
    await cartApi.clearCart();
    setCart(EMPTY_CART);
  };

  // After a successful checkout the server has already emptied the cart: no request is needed to know that.
  const resetCart = useCallback(() => setCart(EMPTY_CART), []);

  return (
    <CartContext.Provider
      value={{
        cartItems: cart.items,
        totalItems: cart.totalItems,
        subtotal: cart.subtotal,
        loading,
        addToCart,
        updateQuantity,
        removeFromCart,
        clearCart,
        refreshCart,
        resetCart,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};
