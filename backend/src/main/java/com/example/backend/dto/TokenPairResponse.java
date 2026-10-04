package com.example.backend.dto;

/** Result of POST /api/v1/auth/refresh. {@code expiresIn} = access-token lifetime in seconds. */
public record TokenPairResponse(String accessToken, String refreshToken, long expiresIn) {
}
