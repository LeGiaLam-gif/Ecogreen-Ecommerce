package com.example.backend.service.notification;

import com.example.backend.config.NotificationAsyncConfig;
import com.example.backend.entity.Notification;
import com.example.backend.entity.NotificationStatus;
import com.example.backend.entity.NotificationType;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.event.OrderCancelledEvent;
import com.example.backend.event.OrderPlacedEvent;
import com.example.backend.event.OrderStatusChangedEvent;
import com.example.backend.event.ReturnCreatedEvent;
import com.example.backend.event.ReturnStatusChangedEvent;
import com.example.backend.repository.NotificationRepository;
import com.example.backend.repository.NotificationSourceRepository;
import com.example.backend.repository.NotificationSourceRepository.OrderRow;
import com.example.backend.repository.NotificationSourceRepository.ReturnRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B10 listener: the event -> notification mapping (same convention as B09's AnalyticsEventListener) and its safety rules. */
class NotificationEventListenerTest {

    private NotificationService notifications;
    private NotificationSourceRepository sources;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        sources = mock(NotificationSourceRepository.class);
        listener = new NotificationEventListener(notifications, sources, mock(PlatformTransactionManager.class));
    }

    private static OrderRow order(Long userId, String total) {
        return new OrderRow() {
            public Long getUserId() { return userId; }
            public BigDecimal getTotalPrice() { return new BigDecimal(total); }
        };
    }

    private static ReturnRow ret(Long userId, Long orderId, String note) {
        return new ReturnRow() {
            public Long getUserId() { return userId; }
            public Long getOrderId() { return orderId; }
            public String getAdminNote() { return note; }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deliveredContext(Long userId, NotificationType type, String key) {
        ArgumentCaptor<Map> ctx = ArgumentCaptor.forClass(Map.class);
        verify(notifications).deliver(eq(userId), eq(type), ctx.capture(), eq(key));
        return (Map<String, Object>) ctx.getValue();
    }

    // ── orders ───────────────────────────────────────────────────────────────

    @Test
    void onOrderPlaced_deliversOrderCreated_withoutAnyLookup() {
        listener.onOrderPlaced(new OrderPlacedEvent(5L, 9L, new BigDecimal("125000.00")));

        Map<String, Object> ctx = deliveredContext(9L, NotificationType.ORDER_CREATED, "order:5:CREATED");
        assertEquals(5L, ctx.get("orderId"));
        assertEquals(new BigDecimal("125000.00"), ctx.get("orderTotal"));
        verifyNoInteractions(sources);
    }

    /** The spec's required test: the REAL service behind the listener writes the row with the ORDER_CREATED template. */
    @Test
    void onOrderCreated_createsNotificationRowWithCorrectTemplate() {
        NotificationRepository repository = mock(NotificationRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(sender.send(any())).thenReturn(EmailSender.Delivery.SENT);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "id", 77L);
            return n;
        });
        when(sources.findContact(9L)).thenReturn(Optional.of(NotificationServiceTest.contact(9L, "lan", "lan@example.test", true)));
        NotificationService real = new NotificationService(repository, sources, sender, new NotificationTemplateRenderer(),
                mock(PlatformTransactionManager.class));
        NotificationEventListener wired = new NotificationEventListener(real, sources, mock(PlatformTransactionManager.class));

        wired.onOrderPlaced(new OrderPlacedEvent(5L, 9L, new BigDecimal("125000.00")));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        Notification row = saved.getValue();
        assertEquals(9L, row.getUserId());
        assertEquals(NotificationType.ORDER_CREATED, row.getType());
        assertEquals("EcoGreen: Đặt hàng thành công", row.getTitle());
        assertTrue(row.getBody().contains("#5"));
        assertTrue(row.getBody().contains("125.000 ₫"));
        assertEquals("order:5:CREATED", row.getDedupeKey());
        verify(repository).updateStatus(eq(77L), eq(NotificationStatus.SENT), any(), isNull());
    }

    @Test
    void onStatusChangedToPaid_sendsPaymentSuccess() {
        when(sources.findOrder(5L)).thenReturn(Optional.of(order(9L, "125000.00")));

        listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, null));

        Map<String, Object> ctx = deliveredContext(9L, NotificationType.PAYMENT_SUCCESS, "order:5:PAID");
        assertEquals(new BigDecimal("125000.00"), ctx.get("orderTotal"));
    }

    @Test
    void onStatusChangedToShippedOrDelivered_sendsThoseNotifications() {
        when(sources.findOrder(5L)).thenReturn(Optional.of(order(9L, "1000.00")));

        listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, OrderStatus.PACKED, OrderStatus.SHIPPED, 3L));
        listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, OrderStatus.SHIPPED, OrderStatus.DELIVERED, 3L));

        deliveredContext(9L, NotificationType.ORDER_SHIPPED, "order:5:SHIPPED");
        deliveredContext(9L, NotificationType.ORDER_DELIVERED, "order:5:DELIVERED");
    }

    @Test
    void aRejectedReturnMovingBackToDelivered_isNotANewDelivery() {
        listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, OrderStatus.RETURN_REQUESTED, OrderStatus.DELIVERED, 3L));

        verifyNoInteractions(notifications);
        verifyNoInteractions(sources);
    }

    @Test
    void onStatusChangedToOtherStatus_sendsNothing() {
        for (OrderStatus target : OrderStatus.values()) {
            if (target == OrderStatus.PAID || target == OrderStatus.SHIPPED || target == OrderStatus.DELIVERED) {
                continue;
            }
            listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, OrderStatus.PENDING_PAYMENT, target, 3L));
        }
        listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, null, null, null));

        verifyNoInteractions(notifications);
        verifyNoInteractions(sources);
    }

    @Test
    void onOrderCancelled_deliversOrderCancelled_withTheRefundFlag() {
        when(sources.findOrder(5L)).thenReturn(Optional.of(order(9L, "125000.00")));

        listener.onOrderCancelled(new OrderCancelledEvent(5L, true));

        Map<String, Object> ctx = deliveredContext(9L, NotificationType.ORDER_CANCELLED, "order:5:CANCELLED");
        assertEquals(true, ctx.get("refundRequired"));
    }

    @Test
    void whenTheOrderCannotBeRead_nothingIsDelivered_andNothingThrows() {
        when(sources.findOrder(404L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> listener.onOrderCancelled(new OrderCancelledEvent(404L, false)));
        assertDoesNotThrow(() -> listener.onOrderStatusChanged(new OrderStatusChangedEvent(404L, null, OrderStatus.PAID, null)));

        verifyNoInteractions(notifications);
    }

    // ── returns ──────────────────────────────────────────────────────────────

    @Test
    void onReturnCreated_deliversReturnCreated() {
        listener.onReturnCreated(new ReturnCreatedEvent(11L, 5L, 9L));

        Map<String, Object> ctx = deliveredContext(9L, NotificationType.RETURN_CREATED, "return:11:CREATED");
        assertEquals(11L, ctx.get("returnId"));
        assertEquals(5L, ctx.get("orderId"));
    }

    @Test
    void onReturnStatusChanged_mapsEachStatusToTemplate() {
        when(sources.findReturn(11L)).thenReturn(Optional.of(ret(9L, 5L, "Thiếu ảnh")));
        Map<ReturnStatus, NotificationType> expected = new EnumMap<>(ReturnStatus.class);
        expected.put(ReturnStatus.APPROVED, NotificationType.RETURN_APPROVED);
        expected.put(ReturnStatus.REJECTED, NotificationType.RETURN_REJECTED);
        expected.put(ReturnStatus.INFO_REQUESTED, NotificationType.RETURN_INFO_REQUESTED);
        expected.put(ReturnStatus.REFUNDED, NotificationType.RETURN_REFUNDED);

        for (ReturnStatus status : ReturnStatus.values()) {
            listener.onReturnStatusChanged(new ReturnStatusChangedEvent(11L, 5L, ReturnStatus.PENDING, status));
        }

        for (Map.Entry<ReturnStatus, NotificationType> e : expected.entrySet()) {
            Map<String, Object> ctx = deliveredContext(9L, e.getValue(), "return:11:" + e.getKey().name());
            assertEquals(11L, ctx.get("returnId"));
            assertEquals(5L, ctx.get("orderId"));
            assertEquals("Thiếu ảnh", ctx.get("note"));
        }
        // PENDING and RETURNED send nothing: exactly the four mapped calls were made
        org.mockito.Mockito.verify(notifications, org.mockito.Mockito.times(4)).deliver(any(), any(), any(), any());
    }

    @Test
    void onReturnStatusChanged_toPendingOrReturned_sendsNothing() {
        listener.onReturnStatusChanged(new ReturnStatusChangedEvent(11L, 5L, ReturnStatus.APPROVED, ReturnStatus.RETURNED));
        listener.onReturnStatusChanged(new ReturnStatusChangedEvent(11L, 5L, null, ReturnStatus.PENDING));
        listener.onReturnStatusChanged(new ReturnStatusChangedEvent(11L, 5L, null, null));

        verifyNoInteractions(notifications);
        verifyNoInteractions(sources);
    }

    // ── safety ───────────────────────────────────────────────────────────────

    @Test
    void listener_neverThrows_evenIfTheServiceOrTheLookupDoes() {
        doThrow(new IllegalStateException("boom")).when(notifications).deliver(any(), any(), any(), any());
        when(sources.findOrder(any())).thenThrow(new IllegalStateException("db"));
        when(sources.findReturn(any())).thenThrow(new IllegalStateException("db"));

        assertDoesNotThrow(() -> listener.onOrderPlaced(new OrderPlacedEvent(5L, 9L, null)));
        assertDoesNotThrow(() -> listener.onOrderStatusChanged(new OrderStatusChangedEvent(5L, null, OrderStatus.PAID, null)));
        assertDoesNotThrow(() -> listener.onOrderCancelled(new OrderCancelledEvent(5L, false)));
        assertDoesNotThrow(() -> listener.onReturnCreated(new ReturnCreatedEvent(1L, 2L, 3L)));
        assertDoesNotThrow(() -> listener.onReturnStatusChanged(new ReturnStatusChangedEvent(1L, 2L, null, ReturnStatus.APPROVED)));
    }

    @Test
    void listener_runsAfterCommit_only() {
        List<Method> handlers = Arrays.stream(NotificationEventListener.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toList();

        assertEquals(5, handlers.size(), "one handler per consumed event");
        for (Method m : handlers) {
            TransactionalEventListener annotation = m.getAnnotation(TransactionalEventListener.class);
            assertNotNull(annotation, m.getName() + " must be a @TransactionalEventListener");
            assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase(), m.getName());
            assertFalse(annotation.fallbackExecution(), m.getName() + " must not run when there is no transaction");
            assertFalse(m.isAnnotationPresent(EventListener.class), m.getName() + " must not be a plain @EventListener");
            Async async = m.getAnnotation(Async.class);
            assertNotNull(async, m.getName() + " must run off the request thread");
            assertEquals(NotificationAsyncConfig.EXECUTOR, async.value(), m.getName());
        }
    }

    @Test
    void theMappingHelpers_coverExactlyTheDocumentedStatuses() {
        assertEquals(NotificationType.PAYMENT_SUCCESS, NotificationEventListener.typeFor(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID));
        assertNull(NotificationEventListener.typeFor(OrderStatus.PAID, OrderStatus.PROCESSING));
        assertNull(NotificationEventListener.typeFor(OrderStatus.PAID, OrderStatus.CANCELLED));
        assertNull(NotificationEventListener.typeFor(null, null));
        assertTrue(Arrays.stream(ReturnStatus.values()).filter(s -> NotificationEventListener.typeFor(s) != null).count() == 4);
    }
}
