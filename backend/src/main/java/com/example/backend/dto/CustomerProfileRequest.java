package com.example.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerProfileRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 20) @Pattern(regexp = "^$|^(\\+?84|0)[ .-]?[0-9]([ .-]?[0-9]){7,9}$",
                message = "Số điện thoại không hợp lệ.") String phone
) {}
