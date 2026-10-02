package com.example.backend.controller;

import com.example.backend.dto.OrderResponse;
import com.example.backend.entity.Order;
import com.example.backend.entity.User;
import com.example.backend.security.AuthGuard;
import com.example.backend.service.OrderService;
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
    @Autowired private AuthGuard authGuard;

    /** Checkout: cart -> order -> order_items -> stock update -> pending payment -> cart cleared. */
    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        Order order = orderService.checkout(
                user,
                body.get("customerName"),
                body.get("customerPhone"),
                body.get("shippingAddress"),
                body.getOrDefault("paymentMethod", "COD")
        );
        return ResponseEntity.status(201).body(OrderResponse.from(order, orderService.getItems(order.getId())));
    }

    /** The current user's own order history. */
    @GetMapping("/my")
    public List<OrderResponse> myOrders(HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        return orderService.getOrdersForUser(user.getId()).stream()
                .map(o -> OrderResponse.from(o, orderService.getItems(o.getId())))
                .collect(Collectors.toList());
    }

    /** Admin: every order. */
    @GetMapping
    public List<OrderResponse> allOrders(HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return orderService.getAllOrders().stream()
                .map(o -> OrderResponse.from(o, orderService.getItems(o.getId())))
                .collect(Collectors.toList());
    }

    /** A user may only fetch their own order; an admin may fetch any order. */
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        boolean isAdmin = authGuard.isAdmin(request);
        Order order = orderService.getOwnedOrAdmin(id, user, isAdmin);
        return OrderResponse.from(order, orderService.getItems(order.getId()));
    }

    @PutMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        Order order = orderService.updateStatus(id, body.get("status"));
        return OrderResponse.from(order, orderService.getItems(order.getId()));
    }
}
