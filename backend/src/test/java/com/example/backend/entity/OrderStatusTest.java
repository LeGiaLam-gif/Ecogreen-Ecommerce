package com.example.backend.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.backend.entity.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B05: the order state machine table. EXPECTED below is written out independently of the production table on purpose, so a
 * change to the table must be made in two places and cannot slip through unnoticed.
 */
class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> EXPECTED = Map.of(
            PENDING_PAYMENT, EnumSet.of(PAID, CANCELLED, PROCESSING),
            PAID, EnumSet.of(PROCESSING, CANCELLED),
            PROCESSING, EnumSet.of(PACKED, CANCELLED),
            PACKED, EnumSet.of(SHIPPED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.of(RETURN_REQUESTED),
            RETURN_REQUESTED, EnumSet.of(RETURNED, DELIVERED),
            RETURNED, EnumSet.of(REFUNDED),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class));

    @Test
    void values_areTheTenSpecifiedStatuses() {
        List<String> names = Arrays.stream(values()).map(Enum::name).toList();
        assertEquals(List.of("PENDING_PAYMENT", "PAID", "PROCESSING", "PACKED", "SHIPPED", "DELIVERED",
                "CANCELLED", "RETURN_REQUESTED", "RETURNED", "REFUNDED"), names);
    }

    @Test
    void names_fitTheVarchar20Column_andAreUpperSnakeCase() {
        for (OrderStatus status : values()) {
            assertTrue(status.name().length() <= 20, status + " must fit orders.status VARCHAR(20)");
            assertTrue(status.name().matches("[A-Z]+(_[A-Z]+)*"), status + " must be UPPER_SNAKE_CASE");
        }
    }

    @Test
    void transition_table_exhaustive() {
        int allowedPairs = 0;
        int checkedPairs = 0;
        for (OrderStatus from : values()) {
            for (OrderStatus to : values()) {
                boolean expected = EXPECTED.get(from).contains(to);
                assertEquals(expected, from.canTransitionTo(to), from + " -> " + to);
                checkedPairs++;
                if (expected) {
                    allowedPairs++;
                }
            }
        }
        assertEquals(100, checkedPairs, "every ordered pair of the 10 statuses is checked");
        assertEquals(13, allowedPairs, "the table has exactly 13 allowed transitions");
    }

    @Test
    void allowedNext_matchesTheTable_forEveryStatus() {
        for (OrderStatus status : values()) {
            assertNotNull(status.allowedNext(), status + " must have an entry (possibly empty)");
            assertEquals(EXPECTED.get(status), status.allowedNext(), status.name());
        }
    }

    @Test
    void transition_invalid_isNotAllowed() {
        assertFalse(DELIVERED.canTransitionTo(PAID));
        assertFalse(CANCELLED.canTransitionTo(PAID));
        assertFalse(PACKED.canTransitionTo(CANCELLED), "a packed order can no longer be cancelled");
        assertFalse(SHIPPED.canTransitionTo(CANCELLED));
        assertFalse(PAID.canTransitionTo(PENDING_PAYMENT), "no going back to unpaid");
        assertFalse(RETURNED.canTransitionTo(DELIVERED));
    }

    @Test
    void transition_sameStatus_isNeverAllowed() {
        for (OrderStatus status : values()) {
            assertFalse(status.canTransitionTo(status), status + " -> " + status);
        }
    }

    @Test
    void canTransitionTo_null_isFalse() {
        for (OrderStatus status : values()) {
            assertFalse(status.canTransitionTo(null));
        }
    }

    @Test
    void terminalStatuses_areExactlyCancelledAndRefunded() {
        for (OrderStatus status : values()) {
            boolean expectedTerminal = status == CANCELLED || status == REFUNDED;
            assertEquals(expectedTerminal, status.isTerminal(), status.name());
            if (expectedTerminal) {
                assertTrue(status.allowedNext().isEmpty(), status + " has no way out");
            }
        }
    }

    @Test
    void everyNonTerminalStatus_isReachableFromPendingPayment() {
        Set<OrderStatus> reached = EnumSet.of(PENDING_PAYMENT);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (OrderStatus status : EnumSet.copyOf(reached)) {
                grew |= reached.addAll(status.allowedNext());
            }
        }
        assertEquals(EnumSet.allOf(OrderStatus.class), reached, "no status may be unreachable");
    }

    @Test
    void countsAsRevenue_isExactlyTheSpecifiedSet() {
        Set<OrderStatus> revenue = EnumSet.of(PAID, PROCESSING, PACKED, SHIPPED, DELIVERED, RETURN_REQUESTED);
        for (OrderStatus status : values()) {
            assertEquals(revenue.contains(status), status.countsAsRevenue(), status.name());
        }
        assertEquals(revenue, OrderStatus.revenueStatuses());
        assertFalse(PENDING_PAYMENT.countsAsRevenue());
        assertFalse(CANCELLED.countsAsRevenue());
        assertFalse(RETURNED.countsAsRevenue());
        assertFalse(REFUNDED.countsAsRevenue());
    }

    @Test
    void exposedSets_areReadOnly() {
        assertThrows(UnsupportedOperationException.class, () -> PAID.allowedNext().add(PENDING_PAYMENT));
        assertThrows(UnsupportedOperationException.class, () -> OrderStatus.revenueStatuses().add(CANCELLED));
    }
}
