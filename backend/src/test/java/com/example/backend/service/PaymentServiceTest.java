package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B05: the one allowed edit to the legacy mock payment - the order status goes through OrderService.transitionStatus. */
class PaymentServiceTest {

    private com.example.backend.repository.PaymentRepository payments;
    private OrderService orderService;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        payments = mock(com.example.backend.repository.PaymentRepository.class);
        orderService = mock(OrderService.class);
        service = new PaymentService();
        ReflectionTestUtils.setField(service, "paymentRepository", payments);
        ReflectionTestUtils.setField(service, "orderService", orderService);
    }

    private Payment paymentFor(OrderStatus orderStatus, Payment.Status paymentStatus) {
        Order order = new Order();
        order.setId(7L);
        order.setStatus(orderStatus);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod("MOMO");
        payment.setStatus(paymentStatus);
        when(payments.findByOrderId(7L)).thenReturn(Optional.of(payment));
        return payment;
    }

    @Test
    void payNow_pendingPaymentOrder_marksPaymentSuccess_andMovesTheOrderThroughTheStateMachine() {
        Payment payment = paymentFor(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING);

        service.payNow(7L);

        assertEquals(Payment.Status.SUCCESS, payment.getStatus());
        assertNotNull(payment.getTransactionId());
        assertTrue(payment.getTransactionId().startsWith("MOCK-"));
        verify(orderService).transitionStatus(7L, OrderStatus.PAID, null, "Mock payment");
    }

    @Test
    void payNow_orderNotPendingPayment_isRejected_andNothingIsWritten() {
        Payment payment = paymentFor(OrderStatus.PROCESSING, Payment.Status.PENDING);

        assertThrows(BadRequestException.class, () -> service.payNow(7L));

        assertEquals(Payment.Status.PENDING, payment.getStatus());
        verify(payments, never()).save(any(Payment.class));
        verify(orderService, never()).transitionStatus(anyLong(), any(OrderStatus.class), any(), anyString());
    }

    @Test
    void payNow_cancelledOrder_isRejected() {
        paymentFor(OrderStatus.CANCELLED, Payment.Status.PENDING);

        assertThrows(BadRequestException.class, () -> service.payNow(7L));

        verify(orderService, never()).transitionStatus(anyLong(), any(OrderStatus.class), any(), anyString());
    }

    @Test
    void payNow_alreadyPaid_isIdempotent_andDoesNotTransitionAgain() {
        Payment payment = paymentFor(OrderStatus.PAID, Payment.Status.SUCCESS);

        Payment result = service.payNow(7L);

        assertEquals(payment, result);
        verify(orderService, never()).transitionStatus(anyLong(), any(OrderStatus.class), any(), anyString());
    }

    @Test
    void payNow_whenTheStateMachineRefuses_theExceptionPropagates_soTheTransactionRollsBack() {
        paymentFor(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING);
        doThrow(new BusinessRuleViolationException("concurrent cancel")).when(orderService)
                .transitionStatus(7L, OrderStatus.PAID, null, "Mock payment");

        assertThrows(BusinessRuleViolationException.class, () -> service.payNow(7L));
    }
}
