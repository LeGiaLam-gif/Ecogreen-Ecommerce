package com.example.backend.repository;

import com.example.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Best-selling products (by quantity) across PAID orders since :from,
     * sorted descending. Revenue here uses the HISTORICAL order_items.price
     * snapshot (quantity * price at time of purchase), never the product's
     * current price.
     * Row shape: [0]=productId, [1]=productName, [2]=quantitySold, [3]=revenue.
     */
    @Query("SELECT oi.product.id, oi.product.name, SUM(oi.quantity), SUM(oi.price * oi.quantity) " +
            "FROM OrderItem oi WHERE oi.order.status = 'PAID' AND oi.order.createdAt >= :from " +
            "GROUP BY oi.product.id, oi.product.name ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> topSellingProductsSince(@Param("from") LocalDateTime from);
}
