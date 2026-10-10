package com.example.backend.service.inventory;

import com.example.backend.entity.OrderStatus;

import java.util.Objects;

/**
 * Decides which stock movement an order status change implies. Pure and stateless, so the rule is easy to test and
 * OrderService.transitionStatus only has to apply the result through {@link InventoryGateway}.
 *
 * <pre>
 * PENDING_PAYMENT -> PAID | PROCESSING   COMMIT   (PROCESSING here is the COD path)
 * PENDING_PAYMENT -> CANCELLED           RELEASE  (the stock was only reserved)
 * PAID | PROCESSING -> CANCELLED         RESTOCK  (the stock was already committed - never RELEASE)
 * everything else, including RETURNED and REFUNDED: NONE (B07 owns restocking returned goods; never restock twice)
 * </pre>
 */
public final class OrderInventoryPolicy {

    public enum Effect { NONE, COMMIT, RELEASE, RESTOCK }

    private OrderInventoryPolicy() {
    }

    public static Effect effectFor(OrderStatus from, OrderStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (from == OrderStatus.PENDING_PAYMENT) {
            if (to == OrderStatus.PAID || to == OrderStatus.PROCESSING) {
                return Effect.COMMIT;
            }
            if (to == OrderStatus.CANCELLED) {
                return Effect.RELEASE;
            }
            return Effect.NONE;
        }
        if ((from == OrderStatus.PAID || from == OrderStatus.PROCESSING) && to == OrderStatus.CANCELLED) {
            return Effect.RESTOCK;
        }
        return Effect.NONE;
    }
}
