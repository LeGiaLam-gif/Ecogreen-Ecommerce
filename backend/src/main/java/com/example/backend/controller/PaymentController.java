package com.example.backend.controller;

import com.example.backend.dto.PaymentResponse;
import com.example.backend.entity.User;
import com.example.backend.security.AuthGuard;
import com.example.backend.service.OrderService;
import com.example.backend.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/** Simulated payment endpoints only - no real payment gateway is contacted. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired private PaymentService paymentService;
    @Autowired private OrderService orderService;
    @Autowired private AuthGuard authGuard;

    @GetMapping("/order/{orderId}")
    public PaymentResponse getForOrder(@PathVariable Long orderId, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        orderService.getOwnedOrAdmin(orderId, user, authGuard.isAdmin(request));
        return PaymentResponse.from(paymentService.getByOrderId(orderId));
    }

    /** "PAY NOW" - mock payment success/failure simulation. */
    @PostMapping("/order/{orderId}/pay")
    public PaymentResponse payNow(@PathVariable Long orderId, HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        orderService.getOwnedOrAdmin(orderId, user, authGuard.isAdmin(request));
        return PaymentResponse.from(paymentService.payNow(orderId));
    }
}
