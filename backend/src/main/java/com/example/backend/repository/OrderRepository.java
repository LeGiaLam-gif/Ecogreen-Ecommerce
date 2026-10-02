package com.example.backend.repository;

import com.example.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
        List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

        List<Order> findAllByOrderByCreatedAtDesc();

        /**
         * Revenue/order-count grouped by CALENDAR DAY, PAID orders only.
         * FUNCTION('DATE', ...) maps to Postgres' date(timestamp) cast function -
         * see application.properties (org.postgresql.Driver) / docker-compose.yml
         * (postgres:16). If this project is ever ported to MySQL, this needs to
         * change (MySQL also has a DATE() function so it *should* still work, but
         * has not been verified against MySQL).
         * Row shape: [0]=day (java.sql.Date), [1]=revenue (BigDecimal), [2]=orderCount
         * (Long).
         */
        @Query("SELECT FUNCTION('DATE', o.createdAt), SUM(o.totalPrice), COUNT(o) " +
                        "FROM Order o WHERE o.status = 'PAID' AND o.createdAt >= :from " +
                        "GROUP BY FUNCTION('DATE', o.createdAt) ORDER BY FUNCTION('DATE', o.createdAt)")
        List<Object[]> sumRevenueByDay(@Param("from") LocalDateTime from);

        /**
         * Same as {@link #sumRevenueByDay}, grouped by CALENDAR MONTH instead.
         * FUNCTION('date_trunc', 'month', ...) maps to Postgres' date_trunc(text,
         * timestamp) - Postgres-specific, not portable to MySQL as-is.
         * Row shape: [0]=month start (Timestamp), [1]=revenue (BigDecimal),
         * [2]=orderCount (Long).
         */
        @Query("SELECT FUNCTION('date_trunc', 'month', o.createdAt), SUM(o.totalPrice), COUNT(o) " +
                        "FROM Order o WHERE o.status = 'PAID' AND o.createdAt >= :from " +
                        "GROUP BY FUNCTION('date_trunc', 'month', o.createdAt) ORDER BY FUNCTION('date_trunc', 'month', o.createdAt)")
        List<Object[]> sumRevenueByMonth(@Param("from") LocalDateTime from);

        /** Count of orders per status, ALL TIME (not filtered by range). */
        @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
        List<Object[]> countAllByStatus();

        /**
         * Total revenue + order count for PAID orders since :from - used for the
         * summary cards.
         */
        @Query("SELECT COALESCE(SUM(o.totalPrice), 0), COUNT(o) " +
                        "FROM Order o WHERE o.status = 'PAID' AND o.createdAt >= :from")
        List<Object[]> sumRevenueAndCountSince(@Param("from") LocalDateTime from);
}
