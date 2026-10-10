package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
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
    @Autowired private OrderService orderService;

    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán cho đơn hàng #" + orderId));
    }

    /**
     * Mock "PAY NOW" action. Always succeeds unless the order is not payable
     * (e.g. already cancelled). Clearly simulated - never a real bank transaction.
     */
    @Transactional
    public Payment payNow(Long orderId) {
        Payment payment = getByOrderId(orderId);
        Order order = payment.getOrder();

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Đơn hàng này đã bị hủy, không thể tiếp tục thanh toán.");
        }
        if (payment.getStatus() == Payment.Status.SUCCESS) {
            return payment; // already paid - idempotent
        }

        // TEMPORARY-COMPAT(B05): only an order that is waiting for payment can be paid; B06 replaces this mock.
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Đơn hàng không ở trạng thái chờ thanh toán, không thể thanh toán.");
        }

        payment.setStatus(Payment.Status.SUCCESS);
        payment.setTransactionId("MOCK-" + UUID.randomUUID());
        paymentRepository.save(payment);

        // The order status has exactly one writer. It re-validates under a row lock, so a concurrent cancel cannot be
        // overwritten; if it refuses, this whole transaction (including the payment above) rolls back.
        orderService.transitionStatus(orderId, OrderStatus.PAID, null, "Mock payment");

        return payment;
    }

    /**
     * Cập nhật phương thức thanh toán (COD / VIETQR / MOMO).
     * Chỉ cho phép khi đơn hàng chưa được thanh toán.
     */
    @Transactional
    public Payment updateMethod(Long orderId, String method) {
        Payment payment = getByOrderId(orderId);
        if (payment.getStatus() == Payment.Status.SUCCESS) {
            throw new BadRequestException("Đơn hàng đã được thanh toán, không thể thay đổi phương thức.");
        }
        payment.setPaymentMethod(method);
        return paymentRepository.save(payment);
    }
}
