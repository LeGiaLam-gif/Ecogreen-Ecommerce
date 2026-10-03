package com.example.backend.security;

/** Identity claims taken from a cryptographically VERIFIED Google ID token payload. */
public record GoogleIdentity(String sub, String email, boolean emailVerified, String name, String picture) {
}
