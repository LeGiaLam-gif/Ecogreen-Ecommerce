package com.example.backend.repository;

import com.example.backend.entity.OrderItem;
import com.example.backend.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    /** Items of one order with their products in ONE query (no lazy product access afterwards). */
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order.id = :orderId ORDER BY oi.id")
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") Long orderId);

    /** Items of many orders with their products in ONE query - the order lists use this instead of one query per order. */
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order.id IN :orderIds ORDER BY oi.id")
    List<OrderItem> findByOrderIdInWithProduct(@Param("orderIds") Collection<Long> orderIds);

    /**
     * Best-selling products (by quantity) across orders whose status is in {@code statuses} (pass
     * {@link OrderStatus#revenueStatuses()}) since :from, sorted descending. Revenue here uses the HISTORICAL
     * order_items.price snapshot (quantity * price at time of purchase), never the product's current price.
     * Row shape: [0]=productId, [1]=productName, [2]=quantitySold, [3]=revenue.
     */
    @Query("SELECT oi.product.id, oi.product.name, SUM(oi.quantity), SUM(oi.price * oi.quantity) " +
            "FROM OrderItem oi WHERE oi.order.status IN :statuses AND oi.order.createdAt >= :from " +
            "GROUP BY oi.product.id, oi.product.name ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> topSellingProductsSince(@Param("from") LocalDateTime from, @Param("statuses") Collection<OrderStatus> statuses);
}
