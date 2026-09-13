package com.example.backend.service;

import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The server-side cart is the source of truth for authenticated users so the
 * same cart is visible across devices (laptop, phone, tablet, ...).
 */
@Service
public class CartService {

    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ProductService productService;

    @Transactional
    public Cart createCartForUser(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart);
    }

    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> createCartForUser(user));
    }

    public List<CartItem> getItems(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user " + userId));
        return cartItemRepository.findByCartId(cart.getId());
    }

    public Long getCartId(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user " + userId))
                .getId();
    }

    @Transactional
    public List<CartItem> addItem(User user, Long productId, int quantity) {
        if (quantity <= 0) throw new BadRequestException("Quantity must be greater than zero.");

        Product product = productService.getById(productId);
        if (product.getStatus() != Product.Status.ACTIVE) {
            throw new BadRequestException("This product is not available.");
        }

        Cart cart = getOrCreateCart(user);
        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

        int newQuantity = quantity + existing.map(CartItem::getQuantity).orElse(0);
        if (newQuantity > product.getStockQuantity()) {
            throw new BadRequestException("Only " + product.getStockQuantity() + " item(s) left in stock.");
        }

        CartItem item = existing.orElseGet(() -> {
            CartItem ci = new CartItem();
            ci.setCart(cart);
            ci.setProduct(product);
            return ci;
        });
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return cartItemRepository.findByCartId(cart.getId());
    }

    @Transactional
    public List<CartItem> updateQuantity(User user, Long cartItemId, int quantity) {
        if (quantity <= 0) throw new BadRequestException("Quantity must be greater than zero.");

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        ensureOwnership(user, item);

        if (quantity > item.getProduct().getStockQuantity()) {
            throw new BadRequestException("Only " + item.getProduct().getStockQuantity() + " item(s) left in stock.");
        }
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return cartItemRepository.findByCartId(item.getCart().getId());
    }

    @Transactional
    public List<CartItem> removeItem(User user, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        ensureOwnership(user, item);
        Long cartId = item.getCart().getId();
        cartItemRepository.delete(item);
        return cartItemRepository.findByCartId(cartId);
    }

    @Transactional
    public void clearCart(Long cartId) {
        cartItemRepository.deleteAllByCartId(cartId);
    }

    private void ensureOwnership(User user, CartItem item) {
        if (!item.getCart().getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Cart item not found: " + item.getId());
        }
    }
}
