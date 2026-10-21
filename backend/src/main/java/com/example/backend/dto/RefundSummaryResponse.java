package com.example.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Refund totals for refunds created on the calendar days {@code from}..{@code to} (both inclusive, database zone), VND.
 * {@code items} always lists PENDING and SUCCESS (zero-filled); FAILED refunds are not money owed or paid and are left out.
 * {@code totalAmount} is the sum of the items.
 */
public record RefundSummaryResponse(LocalDate from, LocalDate to, String currency, List<Item> items, BigDecimal totalAmount) {

    public record Item(String status, long count, BigDecimal totalAmount) {
    }
}
