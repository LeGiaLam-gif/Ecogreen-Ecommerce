package com.example.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POST / PUT /api/v1/admin/discounts. Exactly one of {@code percentOff} / {@code amountOff} must be given (checked by
 * DiscountService, which answers 400 VALIDATION_ERROR). The code is trimmed and stored in upper case. {@code active}
 * defaults to true when omitted.
 */
public record DiscountRequest(
        @NotBlank(message = "Mã giảm giá là bắt buộc.")
        @Size(max = 30, message = "Mã giảm giá tối đa 30 ký tự.")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã giảm giá chỉ gồm chữ, số, dấu gạch ngang hoặc gạch dưới.")
        String code,
        @Min(value = 1, message = "Phần trăm giảm phải từ 1 đến 100.")
        @Max(value = 100, message = "Phần trăm giảm phải từ 1 đến 100.")
        Integer percentOff,
        @DecimalMin(value = "0.01", message = "Số tiền giảm phải lớn hơn 0.")
        @DecimalMax(value = "9999999999.99", message = "Số tiền giảm quá lớn.")
        @Digits(integer = 10, fraction = 2, message = "Số tiền giảm không hợp lệ.")
        BigDecimal amountOff,
        @DecimalMin(value = "0", message = "Đơn tối thiểu không được âm.")
        @DecimalMax(value = "9999999999.99", message = "Đơn tối thiểu quá lớn.")
        @Digits(integer = 10, fraction = 2, message = "Đơn tối thiểu không hợp lệ.")
        BigDecimal minOrderAmount,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        Boolean active) {
}
