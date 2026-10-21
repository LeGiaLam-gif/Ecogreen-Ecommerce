package com.example.backend.service;

import com.example.backend.dto.RefundSummaryResponse;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Refund;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.InventoryRepository.InventoryRow;
import com.example.backend.repository.OrderReportRepository;
import com.example.backend.repository.OrderReportRepository.CustomerFrequencyRow;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.RefundReportRepository;
import com.example.backend.repository.RefundReportRepository.RefundTotalRow;
import com.example.backend.service.inventory.InventoryAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** B09 report calculations with exact numbers; the repositories are mocked, so no SQL runs here (see the Postgres test). */
class ReportServiceAnalyticsTest {

    private OrderReportRepository orders;
    private RefundReportRepository refunds;
    private InventoryAdminService inventory;
    private ReportService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderReportRepository.class);
        refunds = mock(RefundReportRepository.class);
        inventory = mock(InventoryAdminService.class);
        service = new ReportService();
        ReflectionTestUtils.setField(service, "orderReportRepository", orders);
        ReflectionTestUtils.setField(service, "refundReportRepository", refunds);
        ReflectionTestUtils.setField(service, "inventoryAdminService", inventory);
    }

    private static RefundTotalRow refundRow(Refund.Status status, long count, String total) {
        return new RefundTotalRow() {
            public Refund.Status getStatus() { return status; }
            public Long getRefundCount() { return count; }
            public BigDecimal getTotalAmount() { return new BigDecimal(total); }
        };
    }

    @SuppressWarnings("unchecked")
    private static Set<Refund.Status> statusesOf(ArgumentCaptor<Collection> captor) {
        return EnumSet.copyOf((Collection<Refund.Status>) (Collection) captor.getValue());
    }

    // ── refund summary ───────────────────────────────────────────────────────

    @Test
    void refundSummary_sumsByStatus_exactAmounts() {
        when(refunds.totalsByStatus(any(), any(), any())).thenReturn(List.of(
                refundRow(Refund.Status.SUCCESS, 1, "50000.00"),
                refundRow(Refund.Status.PENDING, 2, "150000.50")));

        RefundSummaryResponse summary = service.getRefundSummary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals("VND", summary.currency());
        assertEquals(LocalDate.of(2026, 10, 1), summary.from());
        assertEquals(LocalDate.of(2026, 10, 31), summary.to());
        assertEquals(2, summary.items().size());
        assertEquals("PENDING", summary.items().get(0).status());
        assertEquals(2L, summary.items().get(0).count());
        assertEquals(new BigDecimal("150000.50"), summary.items().get(0).totalAmount());
        assertEquals("SUCCESS", summary.items().get(1).status());
        assertEquals(1L, summary.items().get(1).count());
        assertEquals(new BigDecimal("50000.00"), summary.items().get(1).totalAmount());
        assertEquals(new BigDecimal("200000.50"), summary.totalAmount());
    }

    @Test
    void refundSummary_zeroFillsAMissingStatus() {
        when(refunds.totalsByStatus(any(), any(), any())).thenReturn(List.of(refundRow(Refund.Status.SUCCESS, 3, "90000.00")));

        RefundSummaryResponse summary = service.getRefundSummary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals("PENDING", summary.items().get(0).status());
        assertEquals(0L, summary.items().get(0).count());
        assertEquals(new BigDecimal("0.00"), summary.items().get(0).totalAmount());
        assertEquals(new BigDecimal("90000.00"), summary.totalAmount());
    }

    @Test
    void refundSummary_queriesWholeDaysInclusive_andOnlyPendingAndSuccess() {
        when(refunds.totalsByStatus(any(), any(), any())).thenReturn(List.of());

        service.getRefundSummary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        ArgumentCaptor<Collection> statuses = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refunds).totalsByStatus(statuses.capture(), from.capture(), to.capture());
        assertEquals(EnumSet.of(Refund.Status.PENDING, Refund.Status.SUCCESS), statusesOf(statuses));
        assertFalse(statusesOf(statuses).contains(Refund.Status.FAILED));
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), from.getValue());
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), to.getValue(), "the end day is inclusive: the bound is the next midnight");
    }

    @Test
    void refundSummary_defaultsToTheCurrentMonthUpToToday() {
        when(refunds.totalsByStatus(any(), any(), any())).thenReturn(List.of());

        RefundSummaryResponse summary = service.getRefundSummary(null, null);

        LocalDate today = LocalDate.now(ReportTime.zone());
        assertEquals(today, summary.to());
        assertEquals(today.withDayOfMonth(1), summary.from());
    }

    @Test
    void refundSummary_rejectsAReversedOrTooWideRange_withoutQuerying() {
        assertThrows(BadRequestException.class, () -> service.getRefundSummary(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1)));
        assertThrows(BadRequestException.class, () -> service.getRefundSummary(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 10, 1)));
        verify(refunds, never()).totalsByStatus(any(), any(), any());
    }

    // ── customer frequency ───────────────────────────────────────────────────

    @Test
    void customerFrequency_countsOnlyRevenueStatuses() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CustomerFrequencyRow> page = new PageImpl<>(List.of());
        when(orders.customerFrequency(any(), any(Pageable.class))).thenReturn(page);

        Page<CustomerFrequencyRow> result = service.getCustomerPurchaseFrequency(pageable);

        assertEquals(page, result);
        ArgumentCaptor<Collection> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orders).customerFrequency(statuses.capture(), org.mockito.ArgumentMatchers.eq(pageable));
        @SuppressWarnings("unchecked")
        Set<OrderStatus> used = EnumSet.copyOf((Collection<OrderStatus>) (Collection) statuses.getValue());
        assertEquals(OrderStatus.revenueStatuses(), used);
        for (OrderStatus excluded : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.REFUNDED)) {
            assertFalse(used.contains(excluded), excluded + " is not revenue");
        }
        assertTrue(used.contains(OrderStatus.PAID));
    }

    // ── pending orders ───────────────────────────────────────────────────────

    @Test
    void pendingOrders_countsOnlyPendingPayment() {
        when(orders.countByStatus(OrderStatus.PENDING_PAYMENT)).thenReturn(7L);

        assertEquals(7L, service.getPendingOrdersCount());

        verify(orders).countByStatus(OrderStatus.PENDING_PAYMENT);
        verifyNoMoreInteractions(orders);
    }

    // ── low stock ────────────────────────────────────────────────────────────

    @Test
    void lowStock_usesInventoryNotLegacyStockColumn() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<InventoryRow> page = new PageImpl<>(List.of());
        when(inventory.list(null, true, 5, pageable)).thenReturn(page);

        assertEquals(page, service.getLowStockProducts(5, pageable));

        // the low-stock filter is delegated to the B03 read side (inventory table: on hand - reserved)
        verify(inventory).list(null, true, 5, pageable);
        // and ReportService has no way to read products.stock_quantity: it holds no ProductRepository
        for (Field field : ReportService.class.getDeclaredFields()) {
            assertFalse(ProductRepository.class.isAssignableFrom(field.getType()),
                    "ReportService must not read the frozen products.stock_quantity via " + field.getName());
        }
    }

    @Test
    void lowStock_nullThreshold_isPassedOnSoTheInventoryDefaultApplies() {
        Pageable pageable = PageRequest.of(0, 20);
        when(inventory.list(null, true, null, pageable)).thenReturn(new PageImpl<>(List.of()));

        service.getLowStockProducts(null, pageable);

        verify(inventory).list(null, true, null, pageable);
    }

    @Test
    void reportPageable_defaultsTo20_clampsTo100_andHasNoClientSort() {
        assertEquals(20, ReportService.reportPageable(null, null).getPageSize());
        assertEquals(100, ReportService.reportPageable(0, 5000).getPageSize());
        assertEquals(0, ReportService.reportPageable(-1, 10).getPageNumber());
        assertTrue(ReportService.reportPageable(0, 10).getSort().isUnsorted());
    }
}
