package com.example.backend.repository;

import com.example.backend.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    /** Backs the order timeline; served by idx_order_status_history_order_changed (order_id, changed_at). */
    List<OrderStatusHistory> findByOrderIdOrderByChangedAtAscIdAsc(Long orderId);
}
