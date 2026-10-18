package com.example.backend.repository;

import com.example.backend.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    /** Payments of many orders in one query (the admin list needs the payment method of each order). */
    List<Payment> findByOrderIdIn(Collection<Long> orderIds);

    /**
     * The payment of a gateway attempt, row-locked ({@code SELECT ... FOR UPDATE}): the webhook handler takes this lock first, so
     * a duplicate delivery waits and then sees the final status. Only inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.provider = :provider AND p.transactionId = :transactionId")
    Optional<Payment> findByProviderAndTransactionIdForUpdate(@Param("provider") String provider,
                                                              @Param("transactionId") String transactionId);

    /** The payment of an order, row-locked. Only inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
