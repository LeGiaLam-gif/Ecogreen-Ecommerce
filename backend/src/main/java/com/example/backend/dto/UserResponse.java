package com.example.backend.dto;

import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import java.util.List;
import java.util.stream.Collectors;

public class UserResponse {
    public Long id;
    public String username;
    public String email;
    public boolean active;
    public List<String> roles;

    // Note: password is never included in any API response.
    public static UserResponse from(User u) {
        UserResponse r = new UserResponse();
        r.id = u.getId();
        r.username = u.getUsername();
        r.email = u.getEmail();
        r.active = u.isActive();
        r.roles = u.getRoles().stream().map(Role::getName).collect(Collectors.toList());
        return r;
    }
}
