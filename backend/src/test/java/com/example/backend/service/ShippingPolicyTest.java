package com.example.backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** B04 (decision D-10): the flat shipping fee and the free-shipping threshold, with exact amounts and the exact boundary. */
class ShippingPolicyTest {

    private final ShippingPolicy defaults = new ShippingPolicy(new BigDecimal("30000"), new BigDecimal("500000"));

    @Test
    void shipping_belowThreshold_addsFee_aboveThreshold_free() {
        // just below the threshold: the flat fee applies
        assertEquals(new BigDecimal("30000.00"), defaults.feeFor(new BigDecimal("499999.99")));
        assertEquals(new BigDecimal("30000.00"), defaults.feeFor(new BigDecimal("1.00")));
        // exactly at the threshold: free (the threshold is inclusive)
        assertEquals(new BigDecimal("0.00"), defaults.feeFor(new BigDecimal("500000.00")));
        assertEquals(new BigDecimal("0.00"), defaults.feeFor(new BigDecimal("500000")));
        // above it: free
        assertEquals(new BigDecimal("0.00"), defaults.feeFor(new BigDecimal("500000.01")));
        assertEquals(new BigDecimal("0.00"), defaults.feeFor(new BigDecimal("2500000.00")));
    }

    @Test
    void emptyOrNonPositiveSubtotal_hasNoShippingFee() {
        assertEquals(new BigDecimal("0.00"), defaults.feeFor(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.00"), defaults.feeFor(null));
        assertEquals(new BigDecimal("0.00"), defaults.feeFor(new BigDecimal("-1")));
    }

    @Test
    void feeAndThreshold_areConfigurable() {
        ShippingPolicy custom = new ShippingPolicy(new BigDecimal("15000"), new BigDecimal("200000"));

        assertEquals(new BigDecimal("15000.00"), custom.feeFor(new BigDecimal("199999.99")));
        assertEquals(new BigDecimal("0.00"), custom.feeFor(new BigDecimal("200000.00")));
    }

    @Test
    void zeroFlatFee_meansShippingIsAlwaysFree() {
        ShippingPolicy free = new ShippingPolicy(BigDecimal.ZERO, new BigDecimal("500000"));

        assertEquals(new BigDecimal("0.00"), free.feeFor(new BigDecimal("10000")));
    }

    @Test
    void negativeConfiguration_isRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new ShippingPolicy(new BigDecimal("-1"), new BigDecimal("500000")));
        assertThrows(IllegalArgumentException.class, () -> new ShippingPolicy(new BigDecimal("30000"), new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new ShippingPolicy(null, new BigDecimal("500000")));
    }
}
