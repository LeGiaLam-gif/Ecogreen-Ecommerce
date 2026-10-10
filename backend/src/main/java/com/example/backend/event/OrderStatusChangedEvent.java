package com.example.backend.event;

import com.example.backend.entity.OrderStatus;

/**
 * Published by OrderService.transitionStatus for every status change, inside the transaction that made it. Consumers must
 * listen with {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so they never act on a change that was rolled back.
 *
 * @param actorUserId the user who made the change, or null for a system transition (for example the mock payment)
 */
public record OrderStatusChangedEvent(Long orderId, OrderStatus oldStatus, OrderStatus newStatus, Long actorUserId) {
}
