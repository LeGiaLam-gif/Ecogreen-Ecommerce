package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST / PUT /api/v1/users/me/addresses. The owner is always the caller (never read from the body). {@code isDefault}
 * is optional: null keeps the current value on update; on create the first address is always the default.
 *
 * <p>The phone pattern is deliberately lenient: Vietnamese prefix {@code 0}, {@code 84} or {@code +84}, then 8-10 digits
 * that may be separated by single spaces, dots or dashes.
 */
public record AddressRequest(
        @NotBlank(message = "Tên người nhận là bắt buộc.")
        @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự.")
        String recipientName,
        @NotBlank(message = "Số điện thoại là bắt buộc.")
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
        @Pattern(regexp = "^(\\+?84|0)[ .\\-]?[0-9]([ .\\-]?[0-9]){7,9}$",
                message = "Số điện thoại không hợp lệ.")
        String phone,
        @NotBlank(message = "Địa chỉ (số nhà, đường) là bắt buộc.")
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự.")
        String line1,
        @NotBlank(message = "Phường/xã là bắt buộc.")
        @Size(max = 100, message = "Phường/xã tối đa 100 ký tự.")
        String ward,
        @NotBlank(message = "Quận/huyện là bắt buộc.")
        @Size(max = 100, message = "Quận/huyện tối đa 100 ký tự.")
        String district,
        @NotBlank(message = "Tỉnh/thành phố là bắt buộc.")
        @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự.")
        String province,
        @JsonProperty("isDefault")
        Boolean isDefault) {
}
