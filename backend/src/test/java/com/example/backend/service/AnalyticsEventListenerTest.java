package com.example.backend.service;

import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.event.OrderCancelledEvent;
import com.example.backend.event.OrderPlacedEvent;
import com.example.backend.event.OrderStatusChangedEvent;
import com.example.backend.event.ReturnCreatedEvent;
import com.example.backend.event.ReturnStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** B09 listener: the mapping from the events other modules publish to analytics event types. */
class AnalyticsEventListenerTest {

    private AnalyticsEventService analytics;
    private AnalyticsEventListener listener;

    @BeforeEach
    void setUp() {
        analytics = mock(AnalyticsEventService.class);
        listener = new AnalyticsEventListener(analytics);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(ArgumentCaptor<Map> captor) {
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    void listener_orderPlaced_tracksOrderCreated() {
        listener.onOrderPlaced(new OrderPlacedEvent(5L, 9L, new BigDecimal("125000.00")));

        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        verify(analytics).track(eq("order_created"), eq(9L), isNull(), payload.capture());
        assertEquals(5L, payloadOf(payload).get("orderId"));
        assertEquals(new BigDecimal("125000.00"), payloadOf(payload).get("total"));
    }

    @Test
    void listener_statusChangedToPaid_tracksPaymentSuccess() {
        listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, null));

        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        verify(analytics).trackForOrder(eq("payment_success"), eq(5L), payload.capture());
        assertEquals(OrderStatus.PENDING_PAYMENT, payloadOf(payload).get("previousStatus"));
    }

    @Test
    void listener_otherStatusChange_tracksNothing() {
        for (OrderStatus target : OrderStatus.values()) {
            if (target == OrderStatus.PAID) {
                continue;
            }
            listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, OrderStatus.PENDING_PAYMENT, target, 3L));
        }

        verifyNoInteractions(analytics);
    }

    @Test
    void listener_orderCancelled_tracksOrderCancelledWithRefundFlag() {
        listener.onOrderCancelled(new OrderCancelledEvent(5L, true));

        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        verify(analytics).trackForOrder(eq("order_cancelled"), eq(5L), payload.capture());
        assertEquals(true, payloadOf(payload).get("refundRequired"));
    }

    @Test
    void listener_returnCreated_tracksReturnCreatedForTheCustomer() {
        listener.onReturnCreated(new ReturnCreatedEvent(11L, 5L, 9L));

        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        verify(analytics).track(eq("return_created"), eq(9L), isNull(), payload.capture());
        assertEquals(11L, payloadOf(payload).get("returnId"));
        assertEquals(5L, payloadOf(payload).get("orderId"));
    }

    @Test
    void listener_returnStatusChanged_tracksReturnStatusChanged() {
        listener.onReturnStatusChanged(new ReturnStatusChangedEvent(11L, 5L, ReturnStatus.PENDING, ReturnStatus.APPROVED));

        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        verify(analytics).track(eq("return_status_changed"), isNull(), isNull(), payload.capture());
        assertEquals(ReturnStatus.PENDING, payloadOf(payload).get("oldStatus"));
        assertEquals(ReturnStatus.APPROVED, payloadOf(payload).get("newStatus"));
    }

    @Test
    void listener_neverThrows_evenIfTheServiceDoes() {
        doThrow(new IllegalStateException("boom")).when(analytics).track(any(), any(), any(), any());
        doThrow(new IllegalStateException("boom")).when(analytics).trackForOrder(any(), any(), any());

        assertDoesNotThrow(() -> listener.onOrderPlaced(new OrderPlacedEvent(5L, 9L, null)));
        assertDoesNotThrow(() -> listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, null, OrderStatus.PAID, null)));
        assertDoesNotThrow(() -> listener.onOrderCancelled(new OrderCancelledEvent(5L, false)));
        assertDoesNotThrow(() -> listener.onReturnCreated(new ReturnCreatedEvent(1L, 2L, null)));
        assertDoesNotThrow(() -> listener.onReturnStatusChanged(new ReturnStatusChangedEvent(1L, 2L, null, ReturnStatus.APPROVED)));
    }

    @Test
    void listener_runsAfterCommit_only() {
        List<Method> handlers = Arrays.stream(AnalyticsEventListener.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .toList();

        assertEquals(5, handlers.size(), "one handler per consumed event");
        for (Method m : handlers) {
            TransactionalEventListener annotation = m.getAnnotation(TransactionalEventListener.class);
            assertTrue(annotation != null, m.getName() + " must be a @TransactionalEventListener");
            assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase(), m.getName());
            assertFalse(annotation.fallbackExecution(), m.getName() + " must not run when there is no transaction");
            assertFalse(m.isAnnotationPresent(EventListener.class), m.getName() + " must not be a plain @EventListener");
        }
    }
}
