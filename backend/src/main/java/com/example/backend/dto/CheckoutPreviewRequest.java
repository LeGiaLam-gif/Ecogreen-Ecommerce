package com.example.backend.dto;

import jakarta.validation.constraints.Size;

/** POST /api/v1/checkout/preview. The whole body is optional; the only input is the discount code to try. */
public record CheckoutPreviewRequest(
        @Size(max = 30, message = "Mã giảm giá tối đa 30 ký tự.")
        String discountCode) {
}
