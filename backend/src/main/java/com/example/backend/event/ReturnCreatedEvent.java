package com.example.backend.event;

/**
 * Published by ReturnService when a customer creates a return, inside the same transaction. Listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so a rolled-back return is never announced. B09/B10 consume it.
 */
public record ReturnCreatedEvent(Long returnId, Long orderId, Long userId) {
}
