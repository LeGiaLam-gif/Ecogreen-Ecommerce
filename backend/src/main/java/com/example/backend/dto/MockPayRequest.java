package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** TEMPORARY-COMPAT(B06): body of POST /api/v1/payments/{orderId}/mock-pay (development only). */
public record MockPayRequest(
        @NotBlank(message = "Kết quả là bắt buộc.")
        @Pattern(regexp = "SUCCESS|FAILED", message = "Kết quả phải là SUCCESS hoặc FAILED.") String outcome) {
}
