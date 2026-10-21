package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of PATCH /api/v1/admin/returns/{id}/status. {@code newStatus} is parsed against ReturnStatus by the controller;
 * {@code adminNote} is required when moving to REJECTED or INFO_REQUESTED (checked by the controller and the service).
 */
public record UpdateReturnStatusRequest(
        @NotBlank(message = "Trạng thái mới là bắt buộc.") @Size(max = 30) String newStatus,
        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự.") String adminNote) {
}
