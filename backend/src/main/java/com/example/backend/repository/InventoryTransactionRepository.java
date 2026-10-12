package com.example.backend.repository;

import com.example.backend.entity.InventoryTransaction;
import com.example.backend.entity.InventoryTransaction.ChangeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /**
     * Idempotency pre-check, served by uq_inventory_tx_idempotency (product_id, change_type, reference_type, reference_id).
     * It runs while the product's inventory row is locked, so two replays of the same reference cannot interleave; the
     * unique partial index is the backstop.
     */
    boolean existsByProductIdAndChangeTypeAndReferenceTypeAndReferenceId(
            Long productId, ChangeType changeType, String referenceType, Long referenceId);

    /**
     * One product's ledger, newest first; the caller passes {@code created_at DESC, id DESC}, which idx_inventory_tx_product_created
     * (product_id, created_at DESC, id DESC) serves without a sort step.
     */
    Page<InventoryTransaction> findByProductId(Long productId, Pageable pageable);
}
