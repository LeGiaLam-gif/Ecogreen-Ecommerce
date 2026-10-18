package com.example.backend.dto;

import jakarta.validation.constraints.Size;

/** Optional body of POST /api/v1/payments/{orderId}/initiate. A returnUrl outside the configured base is ignored. */
public record InitiatePaymentRequest(@Size(max = 500, message = "Địa chỉ trả về tối đa 500 ký tự.") String returnUrl) {
}
