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
import java.util.Map;

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
}
