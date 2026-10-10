package com.example.backend.repository;

import com.example.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    /** Payments of many orders in one query (the admin list needs the payment method of each order). */
    List<Payment> findByOrderIdIn(Collection<Long> orderIds);
}
