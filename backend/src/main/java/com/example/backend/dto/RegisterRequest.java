package com.example.backend.dto;

/** Blank checks stay in AuthService.register (single combined Vietnamese message, unchanged behaviour). */
public record RegisterRequest(String username, String email, String password) {
}
