package com.example.backend.security;

/** Resolved identity for the current request, attached by {@link AuthInterceptor}. */
public class CurrentUser {
    private final Long userId;
    private final boolean admin;

    public CurrentUser(Long userId, boolean admin) {
        this.userId = userId;
        this.admin = admin;
    }

    public Long getUserId() { return userId; }
    public boolean isAdmin() { return admin; }
}
