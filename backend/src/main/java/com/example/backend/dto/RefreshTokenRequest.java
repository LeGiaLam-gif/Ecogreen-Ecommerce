package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/v1/auth/refresh and /logout. */
public record RefreshTokenRequest(@NotBlank(message = "Thiếu refresh token.") String refreshToken) {
}
