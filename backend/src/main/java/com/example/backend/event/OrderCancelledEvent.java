package com.example.backend.event;

/**
 * Published by OrderService.transitionStatus whenever an order becomes CANCELLED, inside the same transaction. Listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 *
 * @param refundRequired true when the order's payment had already succeeded, so the customer must be refunded. The refund
 *                       itself is built by B06; until then it is a manual task (also recorded as the history note
 *                       REFUND_REQUIRED).
 */
public record OrderCancelledEvent(Long orderId, boolean refundRequired) {
}
