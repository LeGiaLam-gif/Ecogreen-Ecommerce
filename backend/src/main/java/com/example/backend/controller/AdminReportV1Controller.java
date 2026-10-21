package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.CustomerFrequencyResponse;
import com.example.backend.dto.LowStockItemResponse;
import com.example.backend.dto.PendingOrdersResponse;
import com.example.backend.dto.RefundSummaryResponse;
import com.example.backend.entity.OrderStatus;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.Permissions;
import com.example.backend.service.ReportService;
import com.example.backend.service.ReportTime;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * The B09 admin reports on the new API convention. The legacy revenue reports stay in {@link StatsController}
 * ({@code /api/admin/reports}, requireAdmin, bare JSON) until B12 migrates them; a separate controller keeps the two
 * generations apart. Every endpoint needs {@code analytics:view}.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportV1Controller {

    @Autowired private ReportService reportService;
    @Autowired private AuthGuard authGuard;

    /** Products whose available quantity is at or below {@code threshold} (default 10, 0..1,000,000), lowest first. */
    @GetMapping("/low-stock")
    public ApiResponse<List<LowStockItemResponse>> lowStock(@RequestParam(required = false) Integer threshold,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.ANALYTICS_VIEW);
        return PageResponse.of(
                reportService.getLowStockProducts(threshold, ReportService.reportPageable(page, size)),
                LowStockItemResponse::from);
    }

    @GetMapping("/pending-orders-count")
    public ApiResponse<PendingOrdersResponse> pendingOrdersCount(HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.ANALYTICS_VIEW);
        return ApiResponse.of(new PendingOrdersResponse(
                OrderStatus.PENDING_PAYMENT.name(), reportService.getPendingOrdersCount()));
    }

    /** {@code from} / {@code to}: ISO dates (yyyy-MM-dd), both inclusive; default the current month up to today. */
    @GetMapping("/refund-summary")
    public ApiResponse<RefundSummaryResponse> refundSummary(@RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to,
                                                            HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.ANALYTICS_VIEW);
        LocalDate start = from == null || from.isBlank() ? null : ReportTime.parseDate(from, "from");
        LocalDate end = to == null || to.isBlank() ? null : ReportTime.parseDate(to, "to");
        return ApiResponse.of(reportService.getRefundSummary(start, end));
    }

    /** Orders per customer (revenue statuses only), most orders first. No {@code sort} parameter: the order is fixed. */
    @GetMapping("/customer-frequency")
    public ApiResponse<List<CustomerFrequencyResponse>> customerFrequency(@RequestParam(required = false) Integer page,
                                                                          @RequestParam(required = false) Integer size,
                                                                          HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.ANALYTICS_VIEW);
        return PageResponse.of(
                reportService.getCustomerPurchaseFrequency(ReportService.reportPageable(page, size)),
                CustomerFrequencyResponse::from);
    }
}
