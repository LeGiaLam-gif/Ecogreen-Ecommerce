package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/admin/inventory/{productId}/adjust. {@code quantityDelta} changes on-hand (positive adds, negative
 * removes; 0 and any result below the reserved quantity are rejected by the service). The reason is mandatory.
 */
public record InventoryAdjustRequest(
        @NotNull(message = "Số lượng điều chỉnh là bắt buộc.")
        Integer quantityDelta,
        @NotBlank(message = "Lý do điều chỉnh là bắt buộc.")
        @Size(max = 255, message = "Lý do điều chỉnh tối đa 255 ký tự.")
        String reason) {
}
