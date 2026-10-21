package com.example.backend.controller;

import com.example.backend.dto.RefundSummaryResponse;
import com.example.backend.entity.AnalyticsEvent;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.repository.AnalyticsEventRepository;
import com.example.backend.repository.InventoryRepository.InventoryRow;
import com.example.backend.repository.OrderReportRepository;
import com.example.backend.repository.OrderReportRepository.CustomerFrequencyRow;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.AnalyticsEventService;
import com.example.backend.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B09 admin endpoints through the real GlobalExceptionHandler and the real AuthGuard (standalone MockMvc). The events
 * endpoint uses the REAL AnalyticsEventService over a mocked repository, so type validation is the real code. The request
 * attribute stands in for what AuthInterceptor sets from a verified token.
 */
class AdminAnalyticsReportControllerTest {

    private AnalyticsEventRepository eventRepository;
    private ReportService reportService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        eventRepository = mock(AnalyticsEventRepository.class);
        AnalyticsEventService events = new AnalyticsEventService(eventRepository, mock(OrderReportRepository.class),
                mock(PlatformTransactionManager.class));
        reportService = mock(ReportService.class);

        AdminAnalyticsV1Controller analytics = new AdminAnalyticsV1Controller();
        ReflectionTestUtils.setField(analytics, "analyticsEventService", events);
        ReflectionTestUtils.setField(analytics, "authGuard", new AuthGuard());

        AdminReportV1Controller reports = new AdminReportV1Controller();
        ReflectionTestUtils.setField(reports, "reportService", reportService);
        ReflectionTestUtils.setField(reports, "authGuard", new AuthGuard());

        mvc = MockMvcBuilders.standaloneSetup(analytics, reports).setControllerAdvice(new GlobalExceptionHandler()).build();

        AnalyticsEvent event = new AnalyticsEvent("order_created", 9L, "s1", "{\"orderId\":5,\"total\":125000.00}");
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(event, "createdAt", LocalDateTime.of(2026, 10, 21, 12, 0));
        when(eventRepository.findInRange(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event)));
        when(eventRepository.findByTypeInRange(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event)));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String... permissions) {
        return b.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(42L, List.of("MANAGER"), List.of(permissions)));
    }

    private int statusOf(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    private static final List<String> ENDPOINTS = List.of(
            "/api/v1/admin/analytics/events",
            "/api/v1/admin/reports/low-stock",
            "/api/v1/admin/reports/pending-orders-count",
            "/api/v1/admin/reports/refund-summary",
            "/api/v1/admin/reports/customer-frequency");

    // ── permission matrix ────────────────────────────────────────────────────

    @Test
    void events_withoutPermission_isForbidden() throws Exception {
        mvc.perform(as(get("/api/v1/admin/analytics/events"), Permissions.ORDER_VIEW))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verify(eventRepository, never()).findInRange(any(), any(), any());
    }

    @Test
    void events_withoutToken_isUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void events_withPermission_isOk() throws Exception {
        mvc.perform(as(get("/api/v1/admin/analytics/events"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].eventType").value("order_created"))
                .andExpect(jsonPath("$.data[0].userId").value(9))
                .andExpect(jsonPath("$.data[0].payload.orderId").value(5))
                .andExpect(jsonPath("$.data[0].payload.total").value(125000.00))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.size").value(1));
    }

    @Test
    void everyEndpoint_withoutToken_is401_andWithAnUnrelatedPermission_is403() throws Exception {
        for (String path : ENDPOINTS) {
            assertEquals(401, statusOf(get(path)), path);
            assertEquals(403, statusOf(as(get(path), Permissions.INVENTORY_VIEW)), path);
            assertEquals(403, statusOf(as(get(path), Permissions.ORDER_VIEW)), path);
        }
    }

    @Test
    void everyEndpoint_withAnalyticsView_isOk() throws Exception {
        when(reportService.getLowStockProducts(any(), any(Pageable.class))).thenReturn(new PageImpl<InventoryRow>(List.of()));
        when(reportService.getCustomerPurchaseFrequency(any(Pageable.class))).thenReturn(new PageImpl<CustomerFrequencyRow>(List.of()));
        when(reportService.getRefundSummary(any(), any())).thenReturn(
                new RefundSummaryResponse(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21), "VND", List.of(), new BigDecimal("0.00")));

        for (String path : ENDPOINTS) {
            assertEquals(200, statusOf(as(get(path), Permissions.ANALYTICS_VIEW)), path);
        }
    }

    // ── behaviour ────────────────────────────────────────────────────────────

    @Test
    void events_invalidType_returns400() throws Exception {
        mvc.perform(as(get("/api/v1/admin/analytics/events").param("type", "not_a_type"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(eventRepository, never()).findByTypeInRange(any(), any(), any(), any());
        verify(eventRepository, never()).findInRange(any(), any(), any());
    }

    @Test
    void events_validType_filtersByThatType() throws Exception {
        mvc.perform(as(get("/api/v1/admin/analytics/events").param("type", "order_created"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isOk());
        verify(eventRepository).findByTypeInRange(eq("order_created"), any(), any(), any(Pageable.class));
    }

    @Test
    void events_badDate_andBadSort_return400() throws Exception {
        mvc.perform(as(get("/api/v1/admin/analytics/events").param("from", "2026-10-01"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(as(get("/api/v1/admin/analytics/events").param("sort", "payload,asc"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void pendingOrdersCount_returnsTheCountAndTheDefinition() throws Exception {
        when(reportService.getPendingOrdersCount()).thenReturn(7L);

        mvc.perform(as(get("/api/v1/admin/reports/pending-orders-count"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.count").value(7))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void lowStock_exposesOnlyTheAvailableQuantity_notReservedOrOnHand() throws Exception {
        InventoryRow row = new InventoryRow() {
            public Long getProductId() { return 7L; }
            public String getProductName() { return "Bình giữ nhiệt"; }
            public String getSku() { return "SKU-1"; }
            public Integer getQuantityOnHand() { return 10; }
            public Integer getReservedQuantity() { return 3; }
            public LocalDateTime getUpdatedAt() { return null; }
        };
        when(reportService.getLowStockProducts(eq(5), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row)));

        mvc.perform(as(get("/api/v1/admin/reports/low-stock").param("threshold", "5"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(7))
                .andExpect(jsonPath("$.data[0].available").value(7))
                .andExpect(jsonPath("$.data[0].reservedQuantity").doesNotExist())
                .andExpect(jsonPath("$.data[0].quantityOnHand").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void lowStock_invalidThreshold_isThe400OfTheInventoryRule() throws Exception {
        when(reportService.getLowStockProducts(eq(-1), any(Pageable.class))).thenThrow(new BadRequestException("Ngưỡng tồn kho không hợp lệ."));

        mvc.perform(as(get("/api/v1/admin/reports/low-stock").param("threshold", "-1"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void refundSummary_parsesDates_andReturnsExactAmounts() throws Exception {
        when(reportService.getRefundSummary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).thenReturn(
                new RefundSummaryResponse(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "VND", List.of(
                        new RefundSummaryResponse.Item("PENDING", 2, new BigDecimal("150000.50")),
                        new RefundSummaryResponse.Item("SUCCESS", 1, new BigDecimal("50000.00"))),
                        new BigDecimal("200000.50")));

        mvc.perform(as(get("/api/v1/admin/reports/refund-summary").param("from", "2026-10-01").param("to", "2026-10-31"),
                        Permissions.ANALYTICS_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.from").value("2026-10-01"))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].totalAmount").value(150000.50))
                .andExpect(jsonPath("$.data.items[1].totalAmount").value(50000.00))
                .andExpect(jsonPath("$.data.totalAmount").value(200000.50));
    }

    @Test
    void refundSummary_badDate_returns400_withoutCallingTheService() throws Exception {
        mvc.perform(as(get("/api/v1/admin/reports/refund-summary").param("from", "01/10/2026"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(reportService, never()).getRefundSummary(any(), any());
    }

    @Test
    void customerFrequency_returnsCountsWithoutPersonalData() throws Exception {
        CustomerFrequencyRow row = new CustomerFrequencyRow() {
            public Long getUserId() { return 9L; }
            public Long getOrderCount() { return 4L; }
            public BigDecimal getTotalSpent() { return new BigDecimal("1250000.00"); }
            public LocalDateTime getFirstOrderAt() { return LocalDateTime.of(2026, 1, 5, 8, 0); }
            public LocalDateTime getLastOrderAt() { return LocalDateTime.of(2026, 10, 1, 9, 30); }
        };
        when(reportService.getCustomerPurchaseFrequency(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row)));

        mvc.perform(as(get("/api/v1/admin/reports/customer-frequency"), Permissions.ANALYTICS_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(9))
                .andExpect(jsonPath("$.data[0].orderCount").value(4))
                .andExpect(jsonPath("$.data[0].totalSpent").value(1250000.00))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].username").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }
}
