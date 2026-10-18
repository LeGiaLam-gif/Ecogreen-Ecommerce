package com.example.backend.dto;

import jakarta.validation.constraints.Size;

/** Optional body of POST /api/v1/admin/payments/{orderId}/confirm. */
public record ConfirmPaymentRequest(@Size(max = 500, message = "Ghi chú tối đa 500 ký tự.") String note) {
}
