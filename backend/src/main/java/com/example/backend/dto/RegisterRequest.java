package com.example.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/register}. Shape rules live here; the password policy (length, 72-byte BCrypt limit) is
 * checked in {@code PasswordPolicy} so its message reaches the user (field messages of password fields are masked by
 * GlobalExceptionHandler). A username may not contain '@' so it can never be confused with an e-mail at login.
 */
public record RegisterRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập.")
        @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự.")
        @Pattern(regexp = "^[A-Za-z0-9_.]*$", message = "Tên đăng nhập chỉ gồm chữ cái, số, dấu gạch dưới và dấu chấm.")
        String username,
        @NotBlank(message = "Vui lòng nhập email.")
        @Size(max = 100, message = "Email tối đa 100 ký tự.")
        @Email(message = "Email không hợp lệ.")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email không hợp lệ.")
        String email,
        @NotBlank(message = "Vui lòng nhập mật khẩu.")
        @Size(max = 128, message = "Mật khẩu quá dài.")
        String password) {
}
