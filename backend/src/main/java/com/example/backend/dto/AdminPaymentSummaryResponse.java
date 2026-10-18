package com.example.backend.dto;

import java.math.BigDecimal;

/**
 * What the admin order list needs per order, in one query for the whole page: the payment method and status and, when a refund
 * exists, its status. {@code refundStatus} is null when the order has no refund.
 */
public record AdminPaymentSummaryResponse(Long orderId, String paymentMethod, String paymentStatus, BigDecimal amount,
                                          String refundStatus) {
}
