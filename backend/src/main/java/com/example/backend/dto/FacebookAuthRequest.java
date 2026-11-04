package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/facebook}: ONLY the Facebook user access token; identity is read by the server from Facebook. */
public record FacebookAuthRequest(
        @NotBlank(message = "Thiếu mã xác thực Facebook.")
        @Size(max = 2048, message = "Mã xác thực Facebook không hợp lệ.")
        String accessToken) {
}
