package com.example.backend.event;

import com.example.backend.entity.ReturnStatus;

/**
 * Published by ReturnService.updateStatus for every return status change, inside the transaction that made it. Listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}. B09/B10 consume it.
 */
public record ReturnStatusChangedEvent(Long returnId, Long orderId, ReturnStatus oldStatus, ReturnStatus newStatus) {
}
