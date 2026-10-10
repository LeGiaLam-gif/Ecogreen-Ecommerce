package com.example.backend.dto;

import jakarta.validation.constraints.Size;

/** Optional body of POST /api/v1/orders/{id}/cancel. */
public record CancelOrderRequest(@Size(max = 500, message = "Ghi chú tối đa 500 ký tự.") String note) {
}
