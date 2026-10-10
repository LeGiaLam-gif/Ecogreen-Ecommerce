package com.example.backend.service.inventory;

import com.example.backend.entity.OrderStatus;
import com.example.backend.service.inventory.OrderInventoryPolicy.Effect;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.example.backend.entity.OrderStatus.*;
import static com.example.backend.service.inventory.OrderInventoryPolicy.effectFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** B05: which stock movement each order status change implies (spec section 4). */
class OrderInventoryPolicyTest {

    @Test
    void pendingPayment_toPaid_commitsInventory() {
        assertEquals(Effect.COMMIT, effectFor(PENDING_PAYMENT, PAID));
    }

    @Test
    void cod_pendingPayment_toProcessing_commitsInventory() {
        assertEquals(Effect.COMMIT, effectFor(PENDING_PAYMENT, PROCESSING));
    }

    @Test
    void pendingPayment_toCancelled_releasesInventory() {
        assertEquals(Effect.RELEASE, effectFor(PENDING_PAYMENT, CANCELLED));
    }

    @Test
    void paid_toCancelled_restocksNotReleases() {
        assertEquals(Effect.RESTOCK, effectFor(PAID, CANCELLED));
        assertNotEquals(Effect.RELEASE, effectFor(PAID, CANCELLED),
                "the stock was already committed; releasing it would corrupt the reservation count");
    }

    @Test
    void processing_toCancelled_restocksNotReleases() {
        assertEquals(Effect.RESTOCK, effectFor(PROCESSING, CANCELLED));
        assertNotEquals(Effect.RELEASE, effectFor(PROCESSING, CANCELLED));
    }

    @Test
    void returnedAndRefunded_haveNoInventoryEffect_b07OwnsRestockingReturnedGoods() {
        assertEquals(Effect.NONE, effectFor(RETURN_REQUESTED, RETURNED));
        assertEquals(Effect.NONE, effectFor(RETURNED, REFUNDED));
        assertEquals(Effect.NONE, effectFor(RETURN_REQUESTED, DELIVERED));
    }

    @Test
    void everyAllowedTransition_hasExactlyTheExpectedEffect() {
        // Only these five allowed transitions move stock; the other eight must be NONE.
        Map<String, Effect> expected = Map.of(
                "PENDING_PAYMENT>PAID", Effect.COMMIT,
                "PENDING_PAYMENT>PROCESSING", Effect.COMMIT,
                "PENDING_PAYMENT>CANCELLED", Effect.RELEASE,
                "PAID>CANCELLED", Effect.RESTOCK,
                "PROCESSING>CANCELLED", Effect.RESTOCK);

        int checked = 0;
        for (OrderStatus from : values()) {
            for (OrderStatus to : from.allowedNext()) {
                Effect want = expected.getOrDefault(from + ">" + to, Effect.NONE);
                assertEquals(want, effectFor(from, to), from + " -> " + to);
                checked++;
            }
        }
        assertEquals(13, checked, "all 13 allowed transitions were checked");
    }

    @Test
    void nullArguments_areRejected() {
        assertThrows(NullPointerException.class, () -> effectFor(null, PAID));
        assertThrows(NullPointerException.class, () -> effectFor(PAID, null));
    }
}
