package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Refund;
import com.example.backend.event.OrderCancelledEvent;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.RefundRepository;
import com.example.backend.service.payment.RefundOnCancelListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B06: one PENDING refund row for a paid order that was cancelled; staff completion; the listener never fails a cancel. */
class RefundServiceTest {

    private PaymentRepository payments;
    private RefundRepository refunds;
    private RefundService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        refunds = mock(RefundRepository.class);
        service = new RefundService();
        ReflectionTestUtils.setField(service, "paymentRepository", payments);
        ReflectionTestUtils.setField(service, "refundRepository", refunds);

        Order order = new Order();
        order.setId(7L);
        payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("255000.00"));
        payment.setStatus(Payment.Status.SUCCESS);
        payment.setPaymentMethod("VNPAY");
        when(payments.findByOrderId(7L)).thenReturn(Optional.of(payment));
    }

    @Test
    void refundRow_createdOnceForPaidCancelledOrder() {
        // first call: no refund yet; the repository then "knows" the row (as the partial unique index would enforce)
        when(refunds.existsByOrderIdAndStatusNot(7L, Refund.Status.FAILED)).thenReturn(false, true);

        assertTrue(service.createPendingRefundIfAbsent(7L));
        assertFalse(service.createPendingRefundIfAbsent(7L));

        ArgumentCaptor<Refund> saved = ArgumentCaptor.forClass(Refund.class);
        verify(refunds, times(1)).save(saved.capture());
        Refund refund = saved.getValue();
        assertEquals(Refund.Status.PENDING, refund.getStatus());
        assertEquals(new BigDecimal("255000.00"), refund.getAmount()); // the FULL payment amount
        assertEquals(payment, refund.getPayment());
        assertEquals(7L, refund.getOrder().getId());
        assertNull(refund.getCreatedBy()); // created by the system
        assertNull(refund.getRefundedAt());
    }

    @Test
    void refundRow_notCreated_whenThePaymentIsNotSuccess() {
        for (Payment.Status status : new Payment.Status[] {Payment.Status.PENDING, Payment.Status.FAILED, Payment.Status.REFUNDED}) {
            payment.setStatus(status);

            assertFalse(service.createPendingRefundIfAbsent(7L), status.name());
        }
        verify(refunds, never()).save(any());
    }

    @Test
    void listener_createsARefund_onlyWhenRefundIsRequired() {
        RefundService refundService = mock(RefundService.class);
        RefundOnCancelListener listener = new RefundOnCancelListener();
        ReflectionTestUtils.setField(listener, "refundService", refundService);

        listener.onOrderCancelled(new OrderCancelledEvent(7L, false));
        verifyNoInteractions(refundService);

        listener.onOrderCancelled(new OrderCancelledEvent(7L, true));
        verify(refundService).createPendingRefundIfAbsent(7L);
    }

    @Test
    void listener_neverFailsTheCancellation_whenTheRefundRowCannotBeWritten() {
        RefundService refundService = mock(RefundService.class);
        doThrow(new IllegalStateException("db down")).when(refundService).createPendingRefundIfAbsent(7L);
        RefundOnCancelListener listener = new RefundOnCancelListener();
        ReflectionTestUtils.setField(listener, "refundService", refundService);

        listener.onOrderCancelled(new OrderCancelledEvent(7L, true)); // must not throw
    }

    @Test
    void completeRefund_marksRefundSuccess_andPaymentRefunded() {
        Refund pending = new Refund();
        pending.setOrder(payment.getOrder());
        pending.setPayment(payment);
        pending.setAmount(payment.getAmount());
        pending.setStatus(Refund.Status.PENDING);
        when(refunds.findByOrderIdAndStatusForUpdate(7L, Refund.Status.PENDING)).thenReturn(Optional.of(pending));

        Refund done = service.completeRefund(7L, 5L, " FT123456 ");

        assertEquals(Refund.Status.SUCCESS, done.getStatus());
        assertNotNull(done.getRefundedAt());
        assertEquals("FT123456", done.getProviderTransactionId());
        assertEquals(Payment.Status.REFUNDED, payment.getStatus());
        verify(payments).save(payment);
    }

    @Test
    void completeRefund_withoutAPendingRefund_isNotFound_andChangesNothing() {
        when(refunds.findByOrderIdAndStatusForUpdate(7L, Refund.Status.PENDING)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.completeRefund(7L, 5L, null));
        assertEquals(Payment.Status.SUCCESS, payment.getStatus());
        verify(payments, never()).save(any());
    }
}
