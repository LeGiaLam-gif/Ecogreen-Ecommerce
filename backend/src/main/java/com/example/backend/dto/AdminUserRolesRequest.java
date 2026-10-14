package com.example.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * PATCH /api/v1/admin/users/{id}/roles: the COMPLETE new set of role names (replaces the old set). Names outside
 * CUSTOMER / MANAGER / ADMIN are rejected by the service with 400.
 */
public record AdminUserRolesRequest(
        @NotEmpty(message = "Cần ít nhất một vai trò.")
        @Size(max = 3, message = "Tối đa 3 vai trò.")
        Set<String> roles) {
}
