package com.example.backend.security;

import com.example.backend.entity.Role;

import java.util.List;

/**
 * Resolved identity for the current request, attached by {@link AuthInterceptor}.
 * roles/permissions come from the verified access token (valid for at most the access-token TTL).
 */
public class CurrentUser {
    private final Long userId;
    private final List<String> roles;
    private final List<String> permissions;

    public CurrentUser(Long userId, List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public Long getUserId() { return userId; }
    public List<String> getRoles() { return roles; }
    public List<String> getPermissions() { return permissions; }
    public boolean isAdmin() { return roles.contains(Role.ADMIN); }

    /** True when the (token-borne, database-sourced) permission list contains {@code code}. */
    public boolean hasPermission(String code) { return code != null && permissions.contains(code); }

    /** True when at least one of {@code codes} is held. */
    public boolean hasAnyPermission(String... codes) {
        if (codes == null) return false;
        for (String code : codes) {
            if (hasPermission(code)) return true;
        }
        return false;
    }
}
