package com.example.backend.service;

import com.example.backend.entity.Order;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All revenue/report calculations for the admin "Sales Dashboard" (V6).
 * ASSUMPTION (documented per MASTER_PROMPT_V6 part 5): only orders with
 * status PAID count as revenue - this includes COD orders once the admin
 * has used "confirm COD payment" (OrderService.confirmCodPayment) and
 * MOCK_PAYMENT orders once "PAY NOW" succeeds (PaymentService.payNow).
 * CONFIRMED (COD, cash not yet collected) and PENDING/CANCELLED orders are
 * never counted as revenue - only as an order-status count.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private UserRepository userRepository;

    // ---------------------------------------------------------------
    // Shared range parsing: "7d" | "30d" | "12m" -> start LocalDateTime
    // ---------------------------------------------------------------

    private LocalDateTime resolveFrom(String range) {
        String r = (range == null || range.isBlank()) ? "30d" : range;
        switch (r) {
            case "7d":
                return LocalDate.now().minusDays(6).atStartOfDay();
            case "30d":
                return LocalDate.now().minusDays(29).atStartOfDay();
            case "12m":
                return YearMonth.now().minusMonths(11).atDay(1).atStartOfDay();
            default:
                throw new BadRequestException("Invalid range: " + r + " (expected 7d, 30d or 12m).");
        }
    }

    private String resolveGroupBy(String range, String groupBy) {
        if (groupBy != null && !groupBy.isBlank())
            return groupBy;
        return "12m".equals(range) ? "month" : "day";
    }

    /**
     * Row[0] can come back as java.sql.Date/Timestamp/LocalDate/LocalDateTime
     * depending on driver - normalize it.
     */
    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date d)
            return d.toLocalDate();
        if (value instanceof java.sql.Timestamp t)
            return t.toLocalDateTime().toLocalDate();
        if (value instanceof LocalDateTime ldt)
            return ldt.toLocalDate();
        if (value instanceof LocalDate ld)
            return ld;
        throw new IllegalStateException(
                "Unexpected date type from query: " + (value == null ? "null" : value.getClass()));
    }

    // ---------------------------------------------------------------
    // (a) revenue-over-time
    // ---------------------------------------------------------------

    public List<Map<String, Object>> getRevenueOverTime(String range, String groupByParam) {
        String r = (range == null || range.isBlank()) ? "30d" : range;
        LocalDateTime from = resolveFrom(r);
        String groupBy = resolveGroupBy(r, groupByParam);

        if ("month".equals(groupBy)) {
            List<Object[]> rows = orderRepository.sumRevenueByMonth(from);
            Map<String, Object[]> byMonth = new LinkedHashMap<>();
            for (Object[] row : rows) {
                String key = YearMonth.from(toLocalDate(row[0])).format(MONTH_FMT);
                byMonth.put(key, row);
            }
            List<Map<String, Object>> result = new ArrayList<>();
            YearMonth cursor = YearMonth.from(from.toLocalDate());
            YearMonth end = YearMonth.now();
            while (!cursor.isAfter(end)) {
                String key = cursor.format(MONTH_FMT);
                Object[] row = byMonth.get(key);
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("period", key);
                point.put("revenue", row != null ? row[1] : BigDecimal.ZERO);
                point.put("orderCount", row != null ? row[2] : 0L);
                result.add(point);
                cursor = cursor.plusMonths(1);
            }
            return result;
        } else {
            List<Object[]> rows = orderRepository.sumRevenueByDay(from);
            Map<String, Object[]> byDay = new LinkedHashMap<>();
            for (Object[] row : rows) {
                byDay.put(toLocalDate(row[0]).format(DAY_FMT), row);
            }
            List<Map<String, Object>> result = new ArrayList<>();
            LocalDate cursor = from.toLocalDate();
            LocalDate end = LocalDate.now();
            while (!cursor.isAfter(end)) {
                String key = cursor.format(DAY_FMT);
                Object[] row = byDay.get(key);
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("period", key);
                point.put("revenue", row != null ? row[1] : BigDecimal.ZERO);
                point.put("orderCount", row != null ? row[2] : 0L);
                result.add(point);
                cursor = cursor.plusDays(1);
            }
            return result;
        }
    }

    // ---------------------------------------------------------------
    // (b) orders-by-status
    // ---------------------------------------------------------------

    public Map<String, Long> getOrdersByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Order.Status status : Order.Status.values()) {
            counts.put(status.name(), 0L);
        }
        for (Object[] row : orderRepository.countAllByStatus()) {
            Order.Status status = (Order.Status) row[0];
            counts.put(status.name(), (Long) row[1]);
        }
        return counts;
    }

    // ---------------------------------------------------------------
    // (c) top-products
    // ---------------------------------------------------------------

    public List<Map<String, Object>> getTopProducts(int limit, String range) {
        LocalDateTime from = resolveFrom(range);
        List<Object[]> rows = orderItemRepository.topSellingProductsSince(from);
        List<Map<String, Object>> result = new ArrayList<>();
        int count = Math.max(1, limit);
        for (Object[] row : rows) {
            if (result.size() >= count)
                break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", row[0]);
            item.put("productName", row[1]);
            item.put("quantitySold", row[2]);
            item.put("revenue", row[3]);
            result.add(item);
        }
        return result;
    }

    // ---------------------------------------------------------------
    // (d) summary
    // ---------------------------------------------------------------

    public Map<String, Object> getSummary(String range) {
        LocalDateTime from = resolveFrom(range);
        List<Object[]> rows = orderRepository.sumRevenueAndCountSince(from);
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalOrders = 0L;
        if (!rows.isEmpty()) {
            Object[] row = rows.get(0);
            totalRevenue = row[0] instanceof BigDecimal ? (BigDecimal) row[0] : new BigDecimal(row[0].toString());
            totalOrders = (Long) row[1];
        }
        BigDecimal averageOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalOrders", totalOrders);
        summary.put("averageOrderValue", averageOrderValue);
        // User.createdAt exists on the entity (see entity/User.java), so this field is
        // populated -
        // per MASTER_PROMPT_V6 1.3.d, if it hadn't existed this key would be omitted
        // entirely.
        summary.put("newCustomers", userRepository.countByCreatedAtGreaterThanEqual(from));
        return summary;
    }
}
