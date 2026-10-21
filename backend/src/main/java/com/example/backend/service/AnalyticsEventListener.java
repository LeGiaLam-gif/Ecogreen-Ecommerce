package com.example.backend.service;

import com.example.backend.entity.OrderStatus;
import com.example.backend.event.OrderCancelledEvent;
import com.example.backend.event.OrderPlacedEvent;
import com.example.backend.event.OrderStatusChangedEvent;
import com.example.backend.event.ReturnCreatedEvent;
import com.example.backend.event.ReturnStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the events other modules already publish into analytics rows (B09). Every listener runs AFTER_COMMIT, so a business
 * transaction that rolled back is never recorded; the write itself happens in its own transaction inside
 * {@link AnalyticsEventService}. No method here can throw: a failure is logged and the business flow is untouched.
 *
 * <p>Mapping (the event classes belong to the publishing modules and are not changed):
 * {@code OrderPlacedEvent} -> order_created, {@code OrderStatusChangedEvent(newStatus=PAID)} -> payment_success,
 * {@code OrderCancelledEvent} -> order_cancelled, {@code ReturnCreatedEvent} -> return_created,
 * {@code ReturnStatusChangedEvent} -> return_status_changed.
 */
@Component
public class AnalyticsEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventListener.class);

    private final AnalyticsEventService analytics;

    public AnalyticsEventListener(AnalyticsEventService analytics) {
        this.analytics = analytics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        safely("order_created", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", event.orderId());
            payload.put("total", event.total());
            analytics.track(AnalyticsEventTypes.ORDER_CREATED, event.userId(), null, payload);
        });
    }

    /** Only the change to PAID is recorded (as payment_success); every other status change records nothing. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        safely("payment_success", () -> {
            if (event.newStatus() != OrderStatus.PAID) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("previousStatus", event.oldStatus());
            analytics.trackForOrder(AnalyticsEventTypes.PAYMENT_SUCCESS, event.orderId(), payload);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        safely("order_cancelled", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("refundRequired", event.refundRequired());
            analytics.trackForOrder(AnalyticsEventTypes.ORDER_CANCELLED, event.orderId(), payload);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReturnCreated(ReturnCreatedEvent event) {
        safely("return_created", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("returnId", event.returnId());
            payload.put("orderId", event.orderId());
            analytics.track(AnalyticsEventTypes.RETURN_CREATED, event.userId(), null, payload);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReturnStatusChanged(ReturnStatusChangedEvent event) {
        safely("return_status_changed", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("returnId", event.returnId());
            payload.put("orderId", event.orderId());
            payload.put("oldStatus", event.oldStatus());
            payload.put("newStatus", event.newStatus());
            analytics.track(AnalyticsEventTypes.RETURN_STATUS_CHANGED, null, null, payload);
        });
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Analytics listener for {} failed: {}", what, e.getClass().getSimpleName());
        }
    }
}
