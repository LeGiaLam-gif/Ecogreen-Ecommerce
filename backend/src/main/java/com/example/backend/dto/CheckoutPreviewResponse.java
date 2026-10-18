package com.example.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The server's own numbers for the caller's cart (POST /api/v1/checkout/preview): what checkout would charge right now.
 * Computing it writes nothing and reserves nothing. Problems that would make checkout fail are reported in {@code issues}
 * instead of an error, so the page can show them next to the lines.
 */
public record CheckoutPreviewResponse(List<Line> items, BigDecimal subtotal, BigDecimal discountAmount,
                                      BigDecimal shippingFee, BigDecimal total, List<Issue> issues) {

    /** One cart line priced from the database. {@code available} is the quantity that can be sold right now. */
    public record Line(Long productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal,
                       int available) {
    }

    /**
     * One problem. {@code code} is EMPTY_CART, PRODUCT_UNAVAILABLE, INSUFFICIENT_STOCK or INVALID_DISCOUNT;
     * {@code productId} is set for the two product codes and null otherwise.
     */
    public record Issue(String code, Long productId, String message) {
    }
}
