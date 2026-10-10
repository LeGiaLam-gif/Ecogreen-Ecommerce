package com.example.backend.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The ONE source of truth for order statuses. The database CHECK in V20__order_state_machine.sql mirrors this list
 * (guarded by OrderMigrationsDriftTest).
 *
 * <p>This enum also holds the transition table - the only place it is defined. The table lists the structural
 * transitions only. Two conditions are applied by OrderService.transitionStatus on top of it, because they depend on data
 * outside the enum: PENDING_PAYMENT to PROCESSING is allowed only for COD orders, and CANCELLED from PAID or PROCESSING is
 * staff only (paid money must then be refunded).
 */
public enum OrderStatus {

    PENDING_PAYMENT(false),
    PAID(true),
    PROCESSING(true),
    PACKED(true),
    SHIPPED(true),
    DELIVERED(true),
    CANCELLED(false),
    RETURN_REQUESTED(true),
    RETURNED(false),
    REFUNDED(false);

    private final boolean countsAsRevenue;

    OrderStatus(boolean countsAsRevenue) {
        this.countsAsRevenue = countsAsRevenue;
    }

    /** Revenue reports count these statuses (B05 spec section 9). CANCELLED, RETURNED, REFUNDED and unpaid orders do not. */
    public boolean countsAsRevenue() {
        return countsAsRevenue;
    }

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTransitions();

    private static final Set<OrderStatus> REVENUE_STATUSES = buildRevenueStatuses();

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        Map<OrderStatus, Set<OrderStatus>> table = new EnumMap<>(OrderStatus.class);
        table.put(PENDING_PAYMENT, EnumSet.of(PAID, CANCELLED, PROCESSING));   // PROCESSING: COD orders only
        table.put(PAID, EnumSet.of(PROCESSING, CANCELLED));                    // CANCELLED: staff only, refund required
        table.put(PROCESSING, EnumSet.of(PACKED, CANCELLED));                  // CANCELLED: staff only
        table.put(PACKED, EnumSet.of(SHIPPED));
        table.put(SHIPPED, EnumSet.of(DELIVERED));
        table.put(DELIVERED, EnumSet.of(RETURN_REQUESTED));
        table.put(RETURN_REQUESTED, EnumSet.of(RETURNED, DELIVERED));          // DELIVERED = the return was rejected
        table.put(RETURNED, EnumSet.of(REFUNDED));
        table.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));               // terminal
        table.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));                // terminal

        Map<OrderStatus, Set<OrderStatus>> readOnly = new EnumMap<>(OrderStatus.class);
        for (Map.Entry<OrderStatus, Set<OrderStatus>> entry : table.entrySet()) {
            readOnly.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(readOnly);
    }

    private static Set<OrderStatus> buildRevenueStatuses() {
        Set<OrderStatus> revenue = EnumSet.noneOf(OrderStatus.class);
        for (OrderStatus status : values()) {
            if (status.countsAsRevenue) {
                revenue.add(status);
            }
        }
        return Collections.unmodifiableSet(revenue);
    }

    /** The statuses an order in this status may move to (read-only, never null, empty for terminal statuses). */
    public Set<OrderStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }

    /** True only for a listed transition; a transition to the same status is never allowed. */
    public boolean canTransitionTo(OrderStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** The statuses revenue queries filter on (read-only). Pass it as a query parameter instead of a status literal. */
    public static Set<OrderStatus> revenueStatuses() {
        return REVENUE_STATUSES;
    }
}
