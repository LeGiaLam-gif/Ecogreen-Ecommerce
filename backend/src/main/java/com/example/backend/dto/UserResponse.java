package com.example.backend.dto;

import com.example.backend.entity.Permission;
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
    public List<String> permissions = List.of();

    // Note: password is never included in any API response.
    public static UserResponse from(User u) {
        UserResponse r = new UserResponse();
        r.id = u.getId();
        r.username = u.getUsername();
        r.email = u.getEmail();
        r.active = u.isActive();
        r.roles = u.getRoles().stream().map(Role::getName).collect(Collectors.toList());
        // B01-P3: union of the permissions of all roles, deduplicated + sorted. Role.permissions is EAGER (see Role), so no
        // lazy loading happens here.
        r.permissions = u.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .distinct().sorted().collect(Collectors.toList());
        return r;
    }
}
