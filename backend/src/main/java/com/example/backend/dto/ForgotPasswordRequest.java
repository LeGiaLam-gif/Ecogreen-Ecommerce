package com.example.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/forgot-password}. */
public record ForgotPasswordRequest(
        @NotBlank(message = "Vui lòng nhập email.")
        @Size(max = 100, message = "Email tối đa 100 ký tự.")
        @Email(message = "Email không hợp lệ.")
        String email) {
}
