package com.example.backend.service;

import com.example.backend.api.ApiPaging;
import com.example.backend.dto.RefundSummaryResponse;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Refund;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.InventoryRepository.InventoryRow;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderReportRepository;
import com.example.backend.repository.OrderReportRepository.CustomerFrequencyRow;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.RefundReportRepository;
import com.example.backend.repository.RefundReportRepository.RefundTotalRow;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.inventory.InventoryAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All revenue/report calculations for the admin "Sales Dashboard" (V6).
 * B05: an order counts as revenue when its status is in
 * {@link OrderStatus#revenueStatuses()} (PAID, PROCESSING, PACKED, SHIPPED,
 * DELIVERED, RETURN_REQUESTED) - the statuses whose money was collected or is
 * accepted as cash on delivery. PENDING_PAYMENT, CANCELLED, RETURNED and
 * REFUNDED orders are never revenue, only an order-status count. The set lives
 * in the enum so a new status cannot silently drop out of the reports.
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
    // B09 (read only): inventory read side of B03, and report-only repositories over orders / refunds.
    @Autowired
    private InventoryAdminService inventoryAdminService;
    @Autowired
    private OrderReportRepository orderReportRepository;
    @Autowired
    private RefundReportRepository refundReportRepository;

    /** The refund statuses the summary reports, in display order. FAILED is not money owed or paid, so it is left out. */
    static final List<Refund.Status> REPORTED_REFUND_STATUSES = List.of(Refund.Status.PENDING, Refund.Status.SUCCESS);
    /** Widest refund-summary window (days), so a request cannot scan the whole refunds table. */
    static final int MAX_REFUND_WINDOW_DAYS = 366;

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
            List<Object[]> rows = orderRepository.sumRevenueByMonth(from, OrderStatus.revenueStatuses());
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
            List<Object[]> rows = orderRepository.sumRevenueByDay(from, OrderStatus.revenueStatuses());
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
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status.name(), 0L);
        }
        for (Object[] row : orderRepository.countAllByStatus()) {
            OrderStatus status = (OrderStatus) row[0];
            counts.put(status.name(), (Long) row[1]);
        }
        return counts;
    }

    // ---------------------------------------------------------------
    // (c) top-products
    // ---------------------------------------------------------------

    public List<Map<String, Object>> getTopProducts(int limit, String range) {
        LocalDateTime from = resolveFrom(range);
        List<Object[]> rows = orderItemRepository.topSellingProductsSince(from, OrderStatus.revenueStatuses());
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
        List<Object[]> rows = orderRepository.sumRevenueAndCountSince(from, OrderStatus.revenueStatuses());
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

    // ---------------------------------------------------------------
    // B09 extensions (read only; see docs/ai/contracts/B09-analytics.md)
    // ---------------------------------------------------------------

    /** Page size clamped by {@link ApiPaging} (default 20, max 100). The order is fixed in each query, so there is no sort. */
    public static Pageable reportPageable(Integer page, Integer size) {
        Pageable p = ApiPaging.of(page, size, null, java.util.Map.of(), Sort.unsorted());
        return PageRequest.of(p.getPageNumber(), p.getPageSize());
    }

    /**
     * Products whose AVAILABLE quantity (on hand - reserved, from the B03 {@code inventory} table) is at or below
     * {@code threshold} (null = 10), lowest first. Reads through {@link InventoryAdminService}, never the frozen
     * {@code products.stock_quantity}.
     */
    public Page<InventoryRow> getLowStockProducts(Integer threshold, Pageable pageable) {
        return inventoryAdminService.list(null, true, threshold, pageable);
    }

    /** "Pending" = {@link OrderStatus#PENDING_PAYMENT}: placed, waiting for payment. PAID orders are not pending. */
    public long getPendingOrdersCount() {
        return orderReportRepository.countByStatus(OrderStatus.PENDING_PAYMENT);
    }

    /**
     * Refund count and amount per status (PENDING, SUCCESS) for refunds created on the calendar days {@code from}..{@code to},
     * both inclusive, in the database's local zone. Defaults: {@code to} = today, {@code from} = first day of that month.
     */
    public RefundSummaryResponse getRefundSummary(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ReportTime.zone());
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new BadRequestException("Tham số from phải không sau to.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_REFUND_WINDOW_DAYS) {
            throw new BadRequestException("Khoảng thời gian tối đa là " + MAX_REFUND_WINDOW_DAYS + " ngày.");
        }
        List<RefundTotalRow> rows = refundReportRepository.totalsByStatus(
                REPORTED_REFUND_STATUSES, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        Map<Refund.Status, RefundTotalRow> byStatus = new EnumMap<>(Refund.Status.class);
        for (RefundTotalRow row : rows) {
            byStatus.put(row.getStatus(), row);
        }
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        BigDecimal total = zero;
        List<RefundSummaryResponse.Item> items = new ArrayList<>();
        for (Refund.Status status : REPORTED_REFUND_STATUSES) {
            RefundTotalRow row = byStatus.get(status);
            long count = row == null || row.getRefundCount() == null ? 0L : row.getRefundCount();
            BigDecimal amount = row == null || row.getTotalAmount() == null
                    ? zero : row.getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP);
            items.add(new RefundSummaryResponse.Item(status.name(), count, amount));
            total = total.add(amount);
        }
        return new RefundSummaryResponse(start, end, "VND", items, total);
    }

    /**
     * Orders per customer, counting only orders in {@link OrderStatus#revenueStatuses()}; most orders first. One grouped
     * query in the database (no per-customer query).
     */
    public Page<CustomerFrequencyRow> getCustomerPurchaseFrequency(Pageable pageable) {
        return orderReportRepository.customerFrequency(OrderStatus.revenueStatuses(), pageable);
    }
}
