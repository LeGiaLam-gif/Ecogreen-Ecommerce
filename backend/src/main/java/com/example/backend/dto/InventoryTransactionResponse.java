package com.example.backend.dto;

import com.example.backend.entity.InventoryTransaction;

import java.time.LocalDateTime;

/** One ledger row for the admin. {@code quantity} is positive except for ADJUST, where it is the signed delta. */
public record InventoryTransactionResponse(
        Long id,
        Long productId,
        String changeType,
        int quantity,
        int onHandAfter,
        int reservedAfter,
        String referenceType,
        Long referenceId,
        String reason,
        Long actorUserId,
        LocalDateTime createdAt) {

    public static InventoryTransactionResponse from(InventoryTransaction t) {
        return new InventoryTransactionResponse(t.getId(), t.getProductId(), t.getChangeType().name(), t.getQuantity(),
                t.getOnHandAfter(), t.getReservedAfter(), t.getReferenceType(), t.getReferenceId(), t.getReason(),
                t.getActorUserId(), t.getCreatedAt());
    }
}
