package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of PUT /api/v1/payments/{orderId}/method. The value is checked against PaymentService.SUPPORTED_METHODS. */
public record UpdatePaymentMethodRequest(
        @NotBlank(message = "Phương thức thanh toán là bắt buộc.")
        @Size(max = 20, message = "Phương thức thanh toán không hợp lệ.") String method) {
}
