package com.example.backend.dto;

import com.example.backend.entity.Discount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One discount code as the admin API returns it. */
public record DiscountResponse(Long id, String code, Integer percentOff, BigDecimal amountOff, BigDecimal minOrderAmount,
                               LocalDateTime validFrom, LocalDateTime validTo, boolean active, LocalDateTime createdAt) {

    public static DiscountResponse from(Discount d) {
        return new DiscountResponse(d.getId(), d.getCode(), d.getPercentOff(), d.getAmountOff(), d.getMinOrderAmount(),
                d.getValidFrom(), d.getValidTo(), d.isActive(), d.getCreatedAt());
    }
}
