package com.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The shipping fee of an order (B04, decision D-10): a flat fee, free once the items total reaches a threshold.
 *
 * <p>Configuration ({@code application.properties}, override with the environment variables):
 * {@code ecogreen.shipping.flat-fee} / {@code SHIPPING_FLAT_FEE} (default 30000) and
 * {@code ecogreen.shipping.free-threshold} / {@code FREE_SHIPPING_THRESHOLD} (default 500000), both in VND.
 *
 * <p>The threshold is compared with the items total BEFORE any discount, and it is inclusive: an items total exactly equal
 * to the threshold ships free. So applying a discount code never pushes a customer back under the free-shipping line.
 */
@Component
public class ShippingPolicy {

    private final BigDecimal flatFee;
    private final BigDecimal freeThreshold;

    public ShippingPolicy(@Value("${ecogreen.shipping.flat-fee:30000}") BigDecimal flatFee,
                          @Value("${ecogreen.shipping.free-threshold:500000}") BigDecimal freeThreshold) {
        if (flatFee == null || flatFee.signum() < 0) {
            throw new IllegalArgumentException("ecogreen.shipping.flat-fee must be zero or positive");
        }
        if (freeThreshold == null || freeThreshold.signum() < 0) {
            throw new IllegalArgumentException("ecogreen.shipping.free-threshold must be zero or positive");
        }
        this.flatFee = flatFee.setScale(2, RoundingMode.HALF_UP);
        this.freeThreshold = freeThreshold.setScale(2, RoundingMode.HALF_UP);
    }

    /** The fee for an order whose items add up to {@code subtotal} (0.00 for an empty order or at/above the threshold). */
    public BigDecimal feeFor(BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() <= 0 || subtotal.compareTo(freeThreshold) >= 0) {
            return new BigDecimal("0.00");
        }
        return flatFee;
    }
}
