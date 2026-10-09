package com.example.backend.dto;

import com.example.backend.entity.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/v1/admin/products. {@code status} may only be DRAFT or ACTIVE (default ACTIVE, DRAFT is opt-in; checked in
 * ProductService). {@code image} is the legacy single-image field, kept for the existing admin form.
 */
public record ProductCreateRequest(
        @NotBlank(message = "Tên sản phẩm không được để trống.") @Size(max = 150, message = "Tên sản phẩm tối đa 150 ký tự.")
        String name,
        @Size(max = 20000, message = "Mô tả tối đa 20000 ký tự.")
        String description,
        @NotNull(message = "Giá sản phẩm là bắt buộc.") @DecimalMin(value = "0.00", message = "Giá sản phẩm phải lớn hơn hoặc bằng 0.")
        @Digits(integer = 10, fraction = 2, message = "Giá sản phẩm không hợp lệ.")
        BigDecimal price,
        @DecimalMin(value = "0.00", message = "Giá so sánh phải lớn hơn hoặc bằng 0.")
        @Digits(integer = 10, fraction = 2, message = "Giá so sánh không hợp lệ.")
        BigDecimal comparePrice,
        @NotNull(message = "Số lượng tồn kho là bắt buộc.") @PositiveOrZero(message = "Số lượng tồn kho không được âm.")
        Integer stockQuantity,
        @Size(max = 255, message = "Đường dẫn ảnh tối đa 255 ký tự.")
        String image,
        @NotNull(message = "Vui lòng chọn danh mục cho sản phẩm.")
        Long categoryId,
        @Size(max = 64, message = "SKU tối đa 64 ký tự.")
        String sku,
        @Size(max = 100, message = "Thương hiệu tối đa 100 ký tự.")
        String brand,
        Product.Status status) {
}
