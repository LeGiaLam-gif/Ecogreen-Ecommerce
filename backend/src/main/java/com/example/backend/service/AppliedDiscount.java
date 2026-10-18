package com.example.backend.service;

import java.math.BigDecimal;

/**
 * The result of applying a discount code to an items total: the canonical (upper-case) code and the amount taken off.
 * {@link #none()} stands for "no code supplied": no code, amount 0.00.
 */
public record AppliedDiscount(String code, BigDecimal amount) {

    private static final AppliedDiscount NONE = new AppliedDiscount(null, new BigDecimal("0.00"));

    public static AppliedDiscount none() {
        return NONE;
    }
}
