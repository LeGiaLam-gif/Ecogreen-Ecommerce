package com.example.backend.controller;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.UserResponse;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody Map<String, String> body) {
        User user = authService.register(body.get("username"), body.get("email"), body.get("password"));
        return ResponseEntity.status(201).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody Map<String, String> body) {
        String token = authService.login(body.get("username"), body.get("password"));
        User user = userRepository.findByUsername(body.get("username")).orElseThrow();
        return ResponseEntity.ok(new AuthResponse(token, UserResponse.from(user)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring(7));
        }
        return ResponseEntity.noContent().build();
    }

}
