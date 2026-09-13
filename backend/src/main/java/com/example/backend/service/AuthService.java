package com.example.backend.service;

import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.AuthTokenStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private AuthTokenStore tokenStore;
    @Autowired private CartService cartService;

    @Transactional
    public User register(String username, String email, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank() || email == null || email.isBlank()) {
            throw new BadRequestException("Username, email and password are required.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already taken.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already in use.");
        }

        Role userRole = roleRepository.findByName(Role.USER)
                .orElseThrow(() -> new IllegalStateException("USER role missing - run database init script."));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordHasher.hash(password));
        user.setRoles(new HashSet<>(java.util.List.of(userRole)));

        User saved = userRepository.save(user);
        // Every authenticated user gets exactly one server-side cart (1-1).
        cartService.createCartForUser(saved);
        return saved;
    }

    /** Returns a fresh opaque bearer token for the given credentials. */
    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password."));

        if (!user.isActive()) {
            throw new UnauthorizedException("This account has been deactivated.");
        }
        if (!passwordHasher.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password.");
        }
        return tokenStore.issueToken(user.getId());
    }

    public void logout(String token) {
        tokenStore.revoke(token);
    }
}
