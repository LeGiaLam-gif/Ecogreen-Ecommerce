package com.example.backend.repository;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
        /**
         * Loads the order row with {@code SELECT ... FOR UPDATE}, so two concurrent transitions of the same order (double
         * click, webhook plus admin) run one after the other. Only OrderService.transitionStatus (and the owner-cancel
         * check in front of it) may use this, and only inside a transaction.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT o FROM Order o WHERE o.id = :id")
        Optional<Order> findByIdForUpdate(@Param("id") Long id);

        /**
         * Revenue/order-count grouped by CALENDAR DAY, only orders whose status is in {@code statuses} (pass
         * {@link OrderStatus#revenueStatuses()}, never a literal).
         * FUNCTION('DATE', ...) maps to Postgres' date(timestamp) cast function -
         * see application.properties (org.postgresql.Driver) / docker-compose.yml
         * (postgres:16). If this project is ever ported to MySQL, this needs to
         * change (MySQL also has a DATE() function so it *should* still work, but
         * has not been verified against MySQL).
         * Row shape: [0]=day (java.sql.Date), [1]=revenue (BigDecimal), [2]=orderCount
         * (Long).
         */
        @Query("SELECT FUNCTION('DATE', o.createdAt), SUM(o.totalPrice), COUNT(o) " +
                        "FROM Order o WHERE o.status IN :statuses AND o.createdAt >= :from " +
                        "GROUP BY FUNCTION('DATE', o.createdAt) ORDER BY FUNCTION('DATE', o.createdAt)")
        List<Object[]> sumRevenueByDay(@Param("from") LocalDateTime from, @Param("statuses") Collection<OrderStatus> statuses);

        /**
         * Same as {@link #sumRevenueByDay}, grouped by CALENDAR MONTH instead.
         * FUNCTION('date_trunc', 'month', ...) maps to Postgres' date_trunc(text,
         * timestamp) - Postgres-specific, not portable to MySQL as-is.
         * Row shape: [0]=month start (Timestamp), [1]=revenue (BigDecimal),
         * [2]=orderCount (Long).
         */
        @Query("SELECT FUNCTION('date_trunc', 'month', o.createdAt), SUM(o.totalPrice), COUNT(o) " +
                        "FROM Order o WHERE o.status IN :statuses AND o.createdAt >= :from " +
                        "GROUP BY FUNCTION('date_trunc', 'month', o.createdAt) ORDER BY FUNCTION('date_trunc', 'month', o.createdAt)")
        List<Object[]> sumRevenueByMonth(@Param("from") LocalDateTime from, @Param("statuses") Collection<OrderStatus> statuses);

        /** Count of orders per status, ALL TIME (not filtered by range). */
        @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
        List<Object[]> countAllByStatus();

        /**
         * Total revenue + order count for orders in {@code statuses} since :from - used for the summary cards.
         */
        @Query("SELECT COALESCE(SUM(o.totalPrice), 0), COUNT(o) " +
                        "FROM Order o WHERE o.status IN :statuses AND o.createdAt >= :from")
        List<Object[]> sumRevenueAndCountSince(@Param("from") LocalDateTime from, @Param("statuses") Collection<OrderStatus> statuses);
}
