package com.example.backend.service;

import com.example.backend.dto.CartResponse;
import com.example.backend.entity.Cart;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.CartRepository;
import com.example.backend.service.inventory.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The server-side cart is the source of truth for authenticated users so the
 * same cart is visible across devices (laptop, phone, tablet, ...).
 *
 * <p>B04: every change takes the user's cart row lock ({@code CartRepository.findByUserIdForUpdate}), the same lock checkout
 * holds, so a change cannot land between checkout reading the lines and clearing them. Reading a cart takes no lock and
 * never creates a cart. A cart read is three statements for any number of lines: the cart, the lines with their products
 * (one join), and the available quantities (one batched query).
 */
@Service
public class CartService {

    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ProductService productService;
    @Autowired private InventoryService inventoryService;

    @Transactional
    public Cart createCartForUser(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart);
    }

    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> createCartForUser(user));
    }

    /** The caller's cart. A user without a cart row gets an empty cart (nothing is written). */
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        Optional<Cart> cart = cartRepository.findByUserId(userId);
        return cart.isEmpty() ? CartResponse.empty() : snapshot(cart.get().getId());
    }

    /** Sellable quantity of every product in these cart items with ONE query (see ProductService.loadAvailability). */
    public Map<Long, Integer> availabilityFor(List<CartItem> items) {
        return inventoryService.availableFor(items.stream().map(i -> i.getProduct().getId()).toList());
    }

    @Transactional
    public CartResponse addItem(User user, Long productId, int quantity) {
        if (quantity <= 0) throw new BadRequestException("Số lượng thêm vào giỏ phải lớn hơn 0.");

        Product product = productService.getById(productId);
        if (product.getStatus() != Product.Status.ACTIVE) {
            throw new BadRequestException("Sản phẩm này hiện không còn khả dụng.");
        }

        Cart cart = cartRepository.findByUserIdForUpdate(user.getId()).orElseGet(() -> createCartForUser(user));
        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

        int newQuantity = quantity + existing.map(CartItem::getQuantity).orElse(0);
        int available = inventoryService.available(product.getId());
        if (newQuantity > available) {
            throw new BadRequestException("Kho chỉ còn " + available + " sản phẩm.");
        }

        CartItem item = existing.orElseGet(() -> {
            CartItem ci = new CartItem();
            ci.setCart(cart);
            ci.setProduct(product);
            return ci;
        });
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return snapshot(cart.getId());
    }

    @Transactional
    public CartResponse updateQuantity(User user, Long cartItemId, int quantity) {
        if (quantity <= 0) throw new BadRequestException("Số lượng phải lớn hơn 0.");

        cartRepository.findByUserIdForUpdate(user.getId());
        CartItem item = loadOwnedItem(user, cartItemId);

        int available = inventoryService.available(item.getProduct().getId());
        if (quantity > available) {
            throw new BadRequestException("Kho chỉ còn " + available + " sản phẩm.");
        }
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return snapshot(item.getCart().getId());
    }

    @Transactional
    public CartResponse removeItem(User user, Long cartItemId) {
        cartRepository.findByUserIdForUpdate(user.getId());
        CartItem item = loadOwnedItem(user, cartItemId);
        Long cartId = item.getCart().getId();
        cartItemRepository.delete(item);
        return snapshot(cartId);
    }

    /** Empties the caller's cart (the cart row itself stays). */
    @Transactional
    public void clearCart(User user) {
        cartRepository.findByUserIdForUpdate(user.getId())
                .ifPresent(cart -> cartItemRepository.deleteAllByCartId(cart.getId()));
    }

    /** The line with its product and cart in one query; a line of another user is reported as not found. */
    private CartItem loadOwnedItem(User user, Long cartItemId) {
        CartItem item = cartItemRepository.findByIdWithProductAndCart(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng #" + cartItemId));
        ensureOwnership(user, item);
        return item;
    }

    /** The cart as the API returns it: the lines with their products (one query) and the available quantities (one query). */
    private CartResponse snapshot(Long cartId) {
        List<CartItem> items = cartItemRepository.findByCartIdWithProduct(cartId);
        return CartResponse.from(cartId, items, availabilityFor(items));
    }

    private void ensureOwnership(User user, CartItem item) {
        if (!item.getCart().getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng #" + item.getId());
        }
    }
}
