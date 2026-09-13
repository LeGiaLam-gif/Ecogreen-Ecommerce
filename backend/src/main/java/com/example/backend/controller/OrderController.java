package com.example.backend.controller;

import com.example.backend.dto.OrderResponse;
import com.example.backend.entity.Order;
import com.example.backend.entity.User;
import com.example.backend.entity.Payment;
import com.example.backend.security.AuthGuard;
import com.example.backend.service.OrderService;
import com.example.backend.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private AuthGuard authGuard;

    private OrderResponse toResponse(Order order) {
        Payment payment = null;
        try {
            payment = paymentService.getByOrderId(order.getId());
        } catch (Exception ignored) {
            // No payment row yet (shouldn't normally happen) - respond without payment info.
        }
        return OrderResponse.from(order, orderService.getItems(order.getId()), payment);
    }

    /** Checkout: cart -> order -> order_items -> stock update -> pending payment -> cart cleared. */
    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        Order order = orderService.checkout(
                user,
                body.get("customerName"),
                body.get("customerPhone"),
                body.get("shippingAddress"),
                body.getOrDefault("paymentMethod", "COD"),
                body.get("deliveryNote")
        );
        return ResponseEntity.status(201).body(toResponse(order));
    }

    /** The current user's own order history. */
    @GetMapping("/my")
    public List<OrderResponse> myOrders(HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        return orderService.getOrdersForUser(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Admin: every order. */
    @GetMapping
    public List<OrderResponse> allOrders(HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return orderService.getAllOrders().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** A user may only fetch their own order; an admin may fetch any order. */
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        boolean isAdmin = authGuard.isAdmin(request);
        Order order = orderService.getOwnedOrAdmin(id, user, isAdmin);
        return toResponse(order);
    }

    @PutMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        Order order = orderService.updateStatus(id, body.get("status"));
        return toResponse(order);
    }

    /**
     * Admin-only: "Confirm COD cash collected" - marks a COD order's payment
     * as SUCCESS and the order as PAID, once the courier has actually
     * collected cash from the customer on delivery.
     */
    @PostMapping("/{id}/confirm-cod-payment")
    public OrderResponse confirmCodPayment(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        Order order = orderService.confirmCodPayment(id);
        return toResponse(order);
    }
}
