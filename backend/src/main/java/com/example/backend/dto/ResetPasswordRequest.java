package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/reset-password}: the token from the e-mail link and the new password. */
public record ResetPasswordRequest(
        @NotBlank(message = "Liên kết đặt lại mật khẩu không hợp lệ.")
        @Size(max = 256, message = "Liên kết đặt lại mật khẩu không hợp lệ.")
        String token,
        @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
        @Size(max = 128, message = "Mật khẩu quá dài.")
        String password) {
}
