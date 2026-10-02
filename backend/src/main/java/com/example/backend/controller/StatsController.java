package com.example.backend.controller;

import com.example.backend.security.AuthGuard;
import com.example.backend.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Sales/revenue reporting for the admin "Sales Dashboard" (V6). Kept separate
 * from AdminController (/api/admin/stats, which only returns 3 counts) so
 * that controller isn't overloaded and AdminStats.jsx keeps working unchanged.
 * Every endpoint here requires ADMIN, checked first line via authGuard -
 * including read-only statistics endpoints, no exceptions.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class StatsController {

    @Autowired
    private ReportService reportService;
    @Autowired
    private AuthGuard authGuard;

    /**
     * [{ "period": "2026-09-01", "revenue": 1234000, "orderCount": 5 }, ...] -
     * continuous, zero-filled.
     */
    @GetMapping("/revenue-over-time")
    public List<Map<String, Object>> revenueOverTime(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String groupBy,
            HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return reportService.getRevenueOverTime(range, groupBy);
    }

    /**
     * { "PENDING": 12, "CONFIRMED": 5, "PAID": 88, "CANCELLED": 3 } - all-time, not
     * range-filtered.
     */
    @GetMapping("/orders-by-status")
    public Map<String, Long> ordersByStatus(HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return reportService.getOrdersByStatus();
    }

    /** Top N best-selling products by quantity, within the given range. */
    @GetMapping("/top-products")
    public List<Map<String, Object>> topProducts(
            @RequestParam(required = false, defaultValue = "5") int limit,
            @RequestParam(required = false) String range,
            HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return reportService.getTopProducts(limit, range);
    }

    /**
     * Summary cards: totalRevenue, totalOrders, averageOrderValue, newCustomers.
     */
    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(required = false) String range,
            HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return reportService.getSummary(range);
    }
}
