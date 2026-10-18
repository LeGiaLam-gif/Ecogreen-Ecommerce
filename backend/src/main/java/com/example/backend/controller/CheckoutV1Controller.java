package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.CheckoutPreviewRequest;
import com.example.backend.dto.CheckoutPreviewResponse;
import com.example.backend.dto.CheckoutRequest;
import com.example.backend.dto.OrderResponse;
import com.example.backend.entity.Order;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.CheckoutService;
import com.example.backend.service.OrderService;
import com.example.backend.service.OrderViewer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Checkout on the /api/v1 contract (B04). The caller is the user of the verified token ({@link CurrentUser}); the body can
 * never name another user, a price, a total, a discount value, a shipping fee or a status. The preview is a POST (the discount
 * code travels in the body) but writes nothing.
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutV1Controller {

    @Autowired private CheckoutService checkoutService;
    @Autowired private OrderService orderService;

    /** The server's numbers for the caller's cart: items, subtotal, discount, shipping, total, and any issues. No side effect. */
    @PostMapping("/preview")
    public ApiResponse<CheckoutPreviewResponse> preview(@RequestBody(required = false) @Valid CheckoutPreviewRequest body,
                                                        HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        return ApiResponse.of(checkoutService.preview(current.getUserId(), body == null ? null : body.discountCode()));
    }

    /** Places the order: 201 with the same order shape as {@code GET /api/v1/orders/{id}}. */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(@Valid @RequestBody CheckoutRequest body,
                                                              HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        Order order = checkoutService.checkout(current.getUserId(), body);
        OrderResponse response = orderService.getOrderView(order.getId(),
                new OrderViewer(current.getUserId(), false, false, false));
        return ResponseEntity.status(201).body(ApiResponse.of(response));
    }

    private static CurrentUser requireCurrent(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        }
        return current;
    }
}
