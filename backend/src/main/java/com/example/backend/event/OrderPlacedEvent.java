package com.example.backend.event;

import java.math.BigDecimal;

/**
 * Published by CheckoutService when an order has been placed (B04), as the LAST step of the checkout transaction. Consumers
 * (analytics, notifications) must listen with {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so they never act on an
 * order that was rolled back.
 *
 * @param total the order's final total (items - discount + shipping), VND
 */
public record OrderPlacedEvent(Long orderId, Long userId, BigDecimal total) {
}
