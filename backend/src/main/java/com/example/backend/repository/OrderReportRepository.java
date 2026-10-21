package com.example.backend.repository;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * READ-ONLY report queries over {@code orders} (B09). A separate interface on purpose: {@link OrderRepository} belongs to B05
 * and is not edited here. It extends {@link Repository}, not JpaRepository, so no save/delete method exists on it.
 */
public interface OrderReportRepository extends Repository<Order, Long> {

    /** One customer's purchase frequency; the aggregation runs in the database. */
    interface CustomerFrequencyRow {
        Long getUserId();

        Long getOrderCount();

        BigDecimal getTotalSpent();

        LocalDateTime getFirstOrderAt();

        LocalDateTime getLastOrderAt();
    }

    /** The owner and total of an order, read after commit by the analytics listener. */
    interface OrderOwnerRow {
        Long getUserId();

        BigDecimal getTotalPrice();
    }

    long countByStatus(OrderStatus status);

    /**
     * Orders per customer, only orders whose status is in {@code statuses} (pass {@link OrderStatus#revenueStatuses()}).
     * ONE grouped query plus one count query, whatever the number of customers (no N+1). {@code o.user.id} is the foreign
     * key column, so no join is made. The order is fixed (most orders first, then user id) and no client text reaches it.
     */
    @Query(value = "select o.user.id as userId, count(o) as orderCount, sum(o.totalPrice) as totalSpent, "
            + "min(o.createdAt) as firstOrderAt, max(o.createdAt) as lastOrderAt "
            + "from Order o where o.status in :statuses group by o.user.id "
            + "order by count(o) desc, o.user.id asc",
            countQuery = "select count(distinct o.user.id) from Order o where o.status in :statuses")
    Page<CustomerFrequencyRow> customerFrequency(@Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

    @Query("select o.user.id as userId, o.totalPrice as totalPrice from Order o where o.id = :orderId")
    Optional<OrderOwnerRow> findOwnerRow(@Param("orderId") Long orderId);
}
