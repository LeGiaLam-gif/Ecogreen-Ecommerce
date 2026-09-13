package com.example.backend.controller;

import com.example.backend.dto.CartResponse;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.User;
import com.example.backend.security.AuthGuard;
import com.example.backend.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * The cart always belongs to the AUTHENTICATED user resolved from the bearer
 * token - never to a userId supplied by the client. This is what makes the
 * cart consistent across devices.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired private CartService cartService;
    @Autowired private AuthGuard authGuard;

    @GetMapping
    public CartResponse getCart(HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        var cart = cartService.getOrCreateCart(user);
        List<CartItem> items = cartService.getItems(user.getId());
        return CartResponse.from(cart.getId(), items);
    }

    @PostMapping("/items")
    public CartResponse addItem(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        Long productId = Long.parseLong(body.get("productId").toString());
        int quantity = body.containsKey("quantity") ? Integer.parseInt(body.get("quantity").toString()) : 1;
        List<CartItem> items = cartService.addItem(user, productId, quantity);
        return CartResponse.from(cartService.getCartId(user.getId()), items);
    }

    @PutMapping("/items/{id}")
    public CartResponse updateItem(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        int quantity = Integer.parseInt(body.get("quantity").toString());
        List<CartItem> items = cartService.updateQuantity(user, id, quantity);
        return CartResponse.from(cartService.getCartId(user.getId()), items);
    }

    @DeleteMapping("/items/{id}")
    public CartResponse removeItem(@PathVariable Long id, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        List<CartItem> items = cartService.removeItem(user, id);
        return CartResponse.from(cartService.getCartId(user.getId()), items);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        cartService.clearCart(cartService.getCartId(user.getId()));
        return ResponseEntity.noContent().build();
    }
}
