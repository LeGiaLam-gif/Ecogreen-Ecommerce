package com.example.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /api/v1/admin/inventory/{productId}: set target available stock with a mandatory reason. */
public record InventorySetAvailableRequest(
        @NotNull(message = "Số lượng tồn kho khả dụng là bắt buộc.")
        @Min(value = 0, message = "Số lượng tồn kho không được âm.")
        Integer available,
        @NotBlank(message = "Vui lòng nhập lý do cập nhật tồn kho.")
        @Size(max = 255, message = "Lý do tối đa 255 ký tự.")
        String reason) {
}
