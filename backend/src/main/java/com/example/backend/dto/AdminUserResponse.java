package com.example.backend.dto;

import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Staff view of a user (B08). Small on purpose: no password hash, no Google subject, no permission list. The two counts
 * are filled in the detail view only (they are omitted from list rows, which keeps the list free of per-row queries).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserResponse {
    public Long id;
    public String username;
    public String email;
    public boolean active;
    /** Role names, sorted. */
    public List<String> roles;
    public LocalDateTime createdAt;
    /** Detail view only. */
    public Long orderCount;
    /** Detail view only. */
    public Long returnRequestCount;

    public static AdminUserResponse from(User u) {
        AdminUserResponse r = new AdminUserResponse();
        r.id = u.getId();
        r.username = u.getUsername();
        r.email = u.getEmail();
        r.active = u.isActive();
        r.roles = u.getRoles().stream().map(Role::getName).sorted().toList();
        r.createdAt = u.getCreatedAt();
        return r;
    }

    public static AdminUserResponse from(User u, long orderCount, long returnRequestCount) {
        AdminUserResponse r = from(u);
        r.orderCount = orderCount;
        r.returnRequestCount = returnRequestCount;
        return r;
    }
}
