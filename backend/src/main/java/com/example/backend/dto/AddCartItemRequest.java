package com.example.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** POST /api/v1/cart/items. {@code quantity} defaults to 1 when omitted. */
public record AddCartItemRequest(
        @NotNull(message = "Vui lòng chọn sản phẩm.")
        Long productId,
        @Min(value = 1, message = "Số lượng phải từ 1 đến 1000.")
        @Max(value = 1000, message = "Số lượng phải từ 1 đến 1000.")
        Integer quantity) {

    public int quantityOrOne() {
        return quantity == null ? 1 : quantity;
    }
}
