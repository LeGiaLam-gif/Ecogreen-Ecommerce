package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.AddCartItemRequest;
import com.example.backend.dto.CartResponse;
import com.example.backend.dto.UpdateCartItemRequest;
import com.example.backend.entity.User;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The cart on the /api/v1 contract (B04). The cart always belongs to the AUTHENTICATED user resolved from the bearer token -
 * never to a user id supplied by the client - which is what makes the same cart show up on every device. Every item
 * operation checks that the line belongs to the caller (a foreign line is a 404). Each change answers with the whole updated
 * cart, so the page never has to fetch it again.
 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartV1Controller {

    @Autowired private CartService cartService;
    @Autowired private AuthGuard authGuard;

    @GetMapping
    public ApiResponse<CartResponse> getCart(HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        return ApiResponse.of(cartService.getCart(current.getUserId()));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest body, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        return ApiResponse.of(cartService.addItem(user, body.productId(), body.quantityOrOne()));
    }

    @PatchMapping("/items/{id}")
    public ApiResponse<CartResponse> updateItem(@PathVariable Long id, @Valid @RequestBody UpdateCartItemRequest body,
                                                HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        return ApiResponse.of(cartService.updateQuantity(user, id, body.quantity()));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<CartResponse> removeItem(@PathVariable Long id, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        return ApiResponse.of(cartService.removeItem(user, id));
    }

    /** Empties the cart. */
    @DeleteMapping
    public ResponseEntity<Void> clearCart(HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        cartService.clearCart(user);
        return ResponseEntity.noContent().build();
    }

    private static CurrentUser requireCurrent(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        }
        return current;
    }
}
