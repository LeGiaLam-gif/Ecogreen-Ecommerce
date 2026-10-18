package com.example.backend.service.payment;

import com.example.backend.event.OrderCancelledEvent;
import com.example.backend.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns {@link OrderCancelledEvent} (refundRequired = true) into a PENDING refund row after the cancellation committed. A failure
 * here never undoes or fails the cancellation: it is logged, and the order's history already carries the note REFUND_REQUIRED.
 */
@Component
public class RefundOnCancelListener {

    private static final Logger log = LoggerFactory.getLogger(RefundOnCancelListener.class);

    @Autowired private RefundService refundService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (!event.refundRequired()) {
            return;
        }
        try {
            refundService.createPendingRefundIfAbsent(event.orderId());
        } catch (RuntimeException e) {
            log.error("Could not create the refund record for order {}: {}", event.orderId(), e.getClass().getSimpleName());
        }
    }
}
