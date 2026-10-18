package com.example.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** PATCH /api/v1/cart/items/{id}: the new quantity of the line (not a delta). */
public record UpdateCartItemRequest(
        @NotNull(message = "Số lượng là bắt buộc.")
        @Min(value = 1, message = "Số lượng phải từ 1 đến 1000.")
        @Max(value = 1000, message = "Số lượng phải từ 1 đến 1000.")
        Integer quantity) {
}
