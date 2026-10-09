package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST / PATCH /api/v1/admin/categories. PATCH replaces name, description and parent together (a null {@code parentId}
 * makes the category a root), so the admin form always sends all three.
 */
public record CategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống.") @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự.")
        String name,
        @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự.")
        String description,
        Long parentId) {
}
