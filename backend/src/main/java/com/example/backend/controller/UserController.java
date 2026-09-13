package com.example.backend.controller;

import com.example.backend.dto.UserResponse;
import com.example.backend.entity.User;
import com.example.backend.security.AuthGuard;
import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired private UserService userService;
    @Autowired private AuthGuard authGuard;

    /** The currently authenticated user's own profile. */
    @GetMapping("/me")
    public UserResponse me(HttpServletRequest request) {
        User user = authGuard.requireUser(request);
        return UserResponse.from(user);
    }

    /** Admin-only: list all users. */
    @GetMapping
    public List<UserResponse> getAllUsers(HttpServletRequest request) {
        authGuard.requireAdmin(request);
        return userService.getAllUsers().stream().map(UserResponse::from).collect(Collectors.toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        authGuard.requireAdmin(request);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
