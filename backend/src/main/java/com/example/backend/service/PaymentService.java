package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.Payment;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Simulated payment processing only - no real gateway (VNPay/Momo/Stripe/...)
 * is contacted. Kept behind this service so a real provider could later be
 * substituted without changing the order/checkout logic.
 */
@Service
public class PaymentService {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;

    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order " + orderId));
    }

    /**
     * Mock "PAY NOW" action. Always succeeds unless the order is not payable
     * (e.g. already cancelled). Clearly simulated - never a real bank transaction.
     */
    @Transactional
    public Payment payNow(Long orderId) {
        Payment payment = getByOrderId(orderId);
        Order order = payment.getOrder();

        if (order.getStatus() == Order.Status.CANCELLED) {
            throw new BadRequestException("This order has been cancelled and cannot be paid.");
        }
        if ("COD".equalsIgnoreCase(payment.getPaymentMethod())) {
            throw new BadRequestException(
                    "COD orders are paid in cash on delivery. Use the admin \"confirm COD payment\" action instead.");
        }
        if (payment.getStatus() == Payment.Status.SUCCESS) {
            return payment; // already paid - idempotent
        }

        payment.setStatus(Payment.Status.SUCCESS);
        payment.setTransactionId("MOCK-" + UUID.randomUUID());
        paymentRepository.save(payment);

        order.setStatus(Order.Status.PAID);
        orderRepository.save(order);

        return payment;
    }
}
