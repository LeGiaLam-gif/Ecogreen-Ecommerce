package com.example.backend.repository;

import com.example.backend.entity.Refund;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** True when the order already has a refund that is not FAILED (the table's partial unique index allows only one). */
    boolean existsByOrderIdAndStatusNot(Long orderId, Refund.Status status);

    /** The PENDING refund of an order, locked, so two staff members cannot complete it twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.order.id = :orderId AND r.status = :status")
    Optional<Refund> findByOrderIdAndStatusForUpdate(@Param("orderId") Long orderId, @Param("status") Refund.Status status);

    /** Refunds of many orders in one query (the admin order list). */
    @Query("SELECT r FROM Refund r WHERE r.order.id IN :orderIds ORDER BY r.id")
    List<Refund> findByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);
}
