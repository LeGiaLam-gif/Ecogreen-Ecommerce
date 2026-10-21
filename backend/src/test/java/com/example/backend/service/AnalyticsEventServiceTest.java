package com.example.backend.service;

import com.example.backend.entity.AnalyticsEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.AnalyticsEventRepository;
import com.example.backend.repository.OrderReportRepository;
import com.example.backend.repository.OrderReportRepository.OrderOwnerRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B09 {@code AnalyticsEventService}: track never throws, the payload is sanitised, the list validates its input. */
class AnalyticsEventServiceTest {

    private AnalyticsEventRepository repository;
    private OrderReportRepository orders;
    private PlatformTransactionManager transactions;
    private AnalyticsEventService service;

    @BeforeEach
    void setUp() {
        repository = mock(AnalyticsEventRepository.class);
        orders = mock(OrderReportRepository.class);
        transactions = mock(PlatformTransactionManager.class);
        service = new AnalyticsEventService(repository, orders, transactions);
    }

    private AnalyticsEvent savedEvent() {
        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    // ── track: never throws ──────────────────────────────────────────────────

    @Test
    void track_whenDbFails_doesNotThrow() {
        when(repository.save(any(AnalyticsEvent.class))).thenThrow(new IllegalStateException("db down: secret detail"));

        assertDoesNotThrow(() -> service.track(AnalyticsEventTypes.ORDER_CREATED, 1L, "s1", map("orderId", 5L)));
        verify(repository).save(any(AnalyticsEvent.class));
    }

    @Test
    void track_whenCommitFails_doesNotThrow() {
        doThrow(new IllegalStateException("commit failed")).when(transactions).commit(any());

        assertDoesNotThrow(() -> service.track(AnalyticsEventTypes.ORDER_CREATED, 1L, null, map("orderId", 5L)));
    }

    @Test
    void track_unknownEventType_isIgnoredOrRejectedWithoutThrowing() {
        assertDoesNotThrow(() -> service.track("product_view", 1L, null, map("productId", 3L)));
        assertDoesNotThrow(() -> service.track("ORDER_CREATED", 1L, null, null));
        assertDoesNotThrow(() -> service.track(null, null, null, null));
        assertDoesNotThrow(() -> service.track("x'; DROP TABLE users;--", 1L, null, null));

        verify(repository, never()).save(any(AnalyticsEvent.class));
    }

    @Test
    void track_runsInItsOwnNewTransaction() {
        service.track(AnalyticsEventTypes.ORDER_CREATED, 1L, null, map("orderId", 5L));

        verify(transactions).getTransaction(argThat(def -> def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void track_storesTypeUserSessionAndPayload() {
        service.track(AnalyticsEventTypes.ORDER_CREATED, 9L, "sess-1", map("orderId", 5L, "total", new BigDecimal("125000.00")));

        AnalyticsEvent e = savedEvent();
        assertEquals("order_created", e.getEventType());
        assertEquals(9L, e.getUserId());
        assertEquals("sess-1", e.getSessionId());
        assertEquals("{\"orderId\":5,\"total\":125000.00}", e.getPayload());
    }

    @Test
    void track_dropsSensitiveAndNestedPayloadEntries() {
        Map<String, Object> payload = map(
                "orderId", 5L,
                "customerEmail", "a@b.test",
                "accessToken", "abc",
                "shippingAddress", "1 Nguyễn Huệ",
                "Phone", "0901234567",
                "cardNumber", "4111",
                "password", "x",
                "nested", Map.of("a", 1),
                "list", List.of(1, 2),
                "bad key", "x",
                "note", "ok");

        service.track(AnalyticsEventTypes.ORDER_CREATED, null, null, payload);

        assertEquals("{\"orderId\":5,\"note\":\"ok\"}", savedEvent().getPayload());
    }

    @Test
    void track_withNoUsablePayload_storesNullPayload() {
        service.track(AnalyticsEventTypes.RETURN_CREATED, 1L, null, map("email", "a@b.test"));

        assertNull(savedEvent().getPayload());
    }

    @Test
    void track_truncatesLongSessionIdAndStringValues() {
        service.track(AnalyticsEventTypes.ORDER_CREATED, 1L, "s".repeat(500), map("note", "n".repeat(500)));

        AnalyticsEvent e = savedEvent();
        assertEquals(AnalyticsEventService.MAX_SESSION_ID, e.getSessionId().length());
        assertEquals("{\"note\":\"" + "n".repeat(AnalyticsEventService.MAX_STRING_VALUE) + "\"}", e.getPayload());
    }

    // ── trackForOrder ────────────────────────────────────────────────────────

    private static OrderOwnerRow owner(Long userId, String total) {
        return new OrderOwnerRow() {
            public Long getUserId() { return userId; }
            public BigDecimal getTotalPrice() { return new BigDecimal(total); }
        };
    }

    @Test
    void trackForOrder_addsOwnerAndTotalFromTheOrder() {
        when(orders.findOwnerRow(5L)).thenReturn(Optional.of(owner(9L, "125000.00")));

        service.trackForOrder(AnalyticsEventTypes.PAYMENT_SUCCESS, 5L, map("previousStatus", "PENDING_PAYMENT"));

        AnalyticsEvent e = savedEvent();
        assertEquals("payment_success", e.getEventType());
        assertEquals(9L, e.getUserId());
        assertEquals("{\"orderId\":5,\"previousStatus\":\"PENDING_PAYMENT\",\"orderTotal\":125000.00}", e.getPayload());
    }

    @Test
    void trackForOrder_whenTheLookupFails_stillRecordsWithoutUser_andDoesNotThrow() {
        when(orders.findOwnerRow(5L)).thenThrow(new IllegalStateException("db"));

        assertDoesNotThrow(() -> service.trackForOrder(AnalyticsEventTypes.ORDER_CANCELLED, 5L, map("refundRequired", true)));

        AnalyticsEvent e = savedEvent();
        assertNull(e.getUserId());
        assertEquals("{\"orderId\":5,\"refundRequired\":true}", e.getPayload());
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void pageable_defaultsToNewestFirst_size20_andClampsTo100() {
        Pageable defaults = AnalyticsEventService.pageable(null, null, null);
        assertEquals(0, defaults.getPageNumber());
        assertEquals(20, defaults.getPageSize());
        assertEquals(Sort.Direction.DESC, defaults.getSort().getOrderFor("createdAt").getDirection());

        assertEquals(100, AnalyticsEventService.pageable(0, 1000, null).getPageSize());
        assertEquals(0, AnalyticsEventService.pageable(-3, 10, null).getPageNumber());
    }

    @Test
    void pageable_rejectsASortFieldOutsideTheWhitelist() {
        assertThrows(BadRequestException.class, () -> AnalyticsEventService.pageable(0, 10, "payload,asc"));
        assertThrows(BadRequestException.class, () -> AnalyticsEventService.pageable(0, 10, "createdAt;drop table x"));
        assertEquals(Sort.Direction.ASC, AnalyticsEventService.pageable(0, 10, "eventType,asc").getSort().getOrderFor("eventType").getDirection());
    }

    @Test
    void listEvents_unknownType_isBadRequest_andNothingIsQueried() {
        assertThrows(BadRequestException.class, () -> service.listEvents("nope", null, null, Pageable.unpaged()));
        verify(repository, never()).findByTypeInRange(any(), any(), any(), any());
        verify(repository, never()).findInRange(any(), any(), any());
    }

    private static LocalDateTime local(String isoInstant) {
        return java.time.OffsetDateTime.parse(isoInstant).atZoneSameInstant(ReportTime.zone()).toLocalDateTime();
    }

    @Test
    void listEvents_withType_usesTheTypeAndRangeQuery_inTheDatabaseZone() {
        when(repository.findByTypeInRange(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listEvents(" order_created ", "2026-10-01T00:00:00Z", "2026-10-08T00:00:00Z", Pageable.unpaged());

        verify(repository).findByTypeInRange(eq("order_created"), eq(local("2026-10-01T00:00:00Z")),
                eq(local("2026-10-08T00:00:00Z")), any());
        verify(repository, never()).findInRange(any(), any(), any());
    }

    @Test
    void listEvents_withoutType_usesTheRangeOnlyQuery() {
        when(repository.findInRange(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listEvents("  ", "2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z", Pageable.unpaged());

        verify(repository).findInRange(eq(local("2026-10-01T00:00:00Z")), eq(local("2026-10-02T00:00:00Z")), any());
        verify(repository, never()).findByTypeInRange(any(), any(), any(), any());
    }

    @Test
    void listEvents_defaultWindowIsTheLast30Days() {
        when(repository.findInRange(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listEvents(null, null, null, Pageable.unpaged());

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findInRange(from.capture(), to.capture(), any());
        assertEquals(from.getValue(), to.getValue().minusDays(AnalyticsEventService.DEFAULT_WINDOW_DAYS));
    }

    @Test
    void listEvents_rejectsBadBounds() {
        assertThrows(BadRequestException.class,
                () -> service.listEvents(null, "yesterday", null, Pageable.unpaged()));
        assertThrows(BadRequestException.class,
                () -> service.listEvents(null, "2026-10-01", "2026-10-02", Pageable.unpaged()), "an offset is required");
        assertThrows(BadRequestException.class,
                () -> service.listEvents(null, "2026-10-08T00:00:00Z", "2026-10-01T00:00:00Z", Pageable.unpaged()));
        assertThrows(BadRequestException.class,
                () -> service.listEvents(null, "2024-01-01T00:00:00Z", "2026-10-01T00:00:00Z", Pageable.unpaged()));
        verify(repository, never()).findInRange(any(), any(), any());
    }

    @Test
    void listEvents_acceptsAnOffsetOtherThanUtc() {
        when(repository.findInRange(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listEvents(null, "2026-10-01T07:00:00+07:00", "2026-10-02T07:00:00+07:00", Pageable.unpaged());

        verify(repository).findInRange(eq(local("2026-10-01T00:00:00Z")), eq(local("2026-10-02T00:00:00Z")), any());
        assertTrue(AnalyticsEventTypes.isKnown("order_created"));
    }
}
