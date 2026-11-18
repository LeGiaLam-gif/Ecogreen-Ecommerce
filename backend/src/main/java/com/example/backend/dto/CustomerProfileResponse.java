package com.example.backend.dto;

import com.example.backend.entity.User;

public record CustomerProfileResponse(Long id, String username, String email, String phone) {
    public static CustomerProfileResponse from(User user) {
        return new CustomerProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getPhone());
    }
}
