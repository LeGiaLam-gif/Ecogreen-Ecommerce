package com.example.backend.service;

import com.example.backend.entity.OrderStatus;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B05: every revenue query is given OrderStatus.revenueStatuses(), never a literal PAID. */
class ReportServiceRevenueTest {

    private OrderRepository orders;
    private OrderItemRepository items;
    private ReportService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        items = mock(OrderItemRepository.class);
        service = new ReportService();
        ReflectionTestUtils.setField(service, "orderRepository", orders);
        ReflectionTestUtils.setField(service, "orderItemRepository", items);
        ReflectionTestUtils.setField(service, "userRepository", mock(UserRepository.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<OrderStatus> captured(ArgumentCaptor<Collection> captor) {
        return EnumSet.copyOf((Collection<OrderStatus>) (Collection) captor.getValue());
    }

    private Set<OrderStatus> statusesUsedBy(String queryName) {
        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        switch (queryName) {
            case "day" -> {
                service.getRevenueOverTime("7d", "day");
                verify(orders).sumRevenueByDay(any(LocalDateTime.class), captor.capture());
            }
            case "month" -> {
                service.getRevenueOverTime("12m", "month");
                verify(orders).sumRevenueByMonth(any(LocalDateTime.class), captor.capture());
            }
            case "summary" -> {
                service.getSummary("7d");
                verify(orders).sumRevenueAndCountSince(any(LocalDateTime.class), captor.capture());
            }
            case "top" -> {
                service.getTopProducts(5, "7d");
                verify(items).topSellingProductsSince(any(LocalDateTime.class), captor.capture());
            }
            default -> throw new IllegalArgumentException(queryName);
        }
        return captured(captor);
    }

    @Test
    void revenueQueries_includeNewInProgressStatuses() {
        for (String query : new String[] {"day", "month", "summary", "top"}) {
            setUp();
            Set<OrderStatus> used = statusesUsedBy(query);
            assertTrue(used.containsAll(EnumSet.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.PACKED,
                    OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED)), query + ": " + used);
        }
    }

    @Test
    void revenueQueries_excludeCancelledReturnedRefunded_andUnpaid() {
        for (String query : new String[] {"day", "month", "summary", "top"}) {
            setUp();
            Set<OrderStatus> used = statusesUsedBy(query);
            assertFalse(used.contains(OrderStatus.PENDING_PAYMENT), query);
            assertFalse(used.contains(OrderStatus.CANCELLED), query);
            assertFalse(used.contains(OrderStatus.RETURNED), query);
            assertFalse(used.contains(OrderStatus.REFUNDED), query);
            assertEquals(OrderStatus.revenueStatuses(), used, query);
        }
    }

    @Test
    void ordersByStatus_reportsAllTenStatuses_includingZeroes() {
        when(orders.countAllByStatus()).thenReturn(java.util.List.<Object[]>of(new Object[] {OrderStatus.PAID, 3L}));

        Map<String, Long> counts = service.getOrdersByStatus();

        assertEquals(10, counts.size());
        assertEquals(3L, counts.get("PAID"));
        assertEquals(0L, counts.get("PENDING_PAYMENT"));
        assertEquals(0L, counts.get("REFUNDED"));
    }
}
