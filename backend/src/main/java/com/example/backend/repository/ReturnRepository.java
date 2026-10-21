package com.example.backend.repository;

import com.example.backend.entity.Return;
import com.example.backend.entity.ReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

/**
 * Returns (B07). The JPA entity is named {@code ReturnRequest} (see {@link Return}), so the JPQL below says
 * {@code ReturnRequest}. Lists fetch the customer in the same query, so a page needs no query per row.
 */
public interface ReturnRepository extends JpaRepository<Return, Long> {

    /** The return row, locked, so two staff members cannot move it at the same time. Only inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReturnRequest r WHERE r.id = :id")
    Optional<Return> findByIdForUpdate(@Param("id") Long id);

    /** One return with its customer (the detail view). */
    @Query("SELECT r FROM ReturnRequest r JOIN FETCH r.user WHERE r.id = :id")
    Optional<Return> findByIdWithUser(@Param("id") Long id);

    /** True when the order already has a return in one of {@code statuses} (served by idx_returns_order_status). */
    @Query("SELECT count(r) > 0 FROM ReturnRequest r WHERE r.orderId = :orderId AND r.status IN :statuses")
    boolean existsOpenReturn(@Param("orderId") Long orderId, @Param("statuses") Collection<ReturnStatus> statuses);

    /** The caller's returns (served by idx_returns_user_created). */
    @Query(value = "SELECT r FROM ReturnRequest r JOIN FETCH r.user WHERE r.userId = :userId",
            countQuery = "SELECT count(r) FROM ReturnRequest r WHERE r.userId = :userId")
    Page<Return> findByUser(@Param("userId") Long userId, Pageable pageable);

    /** Every return, for staff. */
    @Query(value = "SELECT r FROM ReturnRequest r JOIN FETCH r.user",
            countQuery = "SELECT count(r) FROM ReturnRequest r")
    Page<Return> findAllForAdmin(Pageable pageable);

    /** Returns in one status, for staff (served by idx_returns_status_created). */
    @Query(value = "SELECT r FROM ReturnRequest r JOIN FETCH r.user WHERE r.status = :status",
            countQuery = "SELECT count(r) FROM ReturnRequest r WHERE r.status = :status")
    Page<Return> findByStatusForAdmin(@Param("status") ReturnStatus status, Pageable pageable);
}
