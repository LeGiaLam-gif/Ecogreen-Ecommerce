package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of PATCH /api/v1/admin/orders/{id}/status. {@code newStatus} is parsed against the OrderStatus enum by the controller. */
public record UpdateOrderStatusRequest(
        @NotBlank(message = "Trạng thái mới là bắt buộc.") @Size(max = 30) String newStatus,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.") String note) {
}
