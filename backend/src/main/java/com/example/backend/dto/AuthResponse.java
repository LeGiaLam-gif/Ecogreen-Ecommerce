package com.example.backend.dto;

/** Login / Google login result (B01-P2 contract). {@code expiresIn} = access-token lifetime in seconds. */
public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
}
