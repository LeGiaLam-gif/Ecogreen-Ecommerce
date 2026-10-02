package com.example.backend.service;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.GoogleAuthRequest;
import com.example.backend.dto.UserResponse;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            throw new BadRequestException("Vui lòng nhập đầy đủ tên đăng nhập, email và mật khẩu.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Tên đăng nhập này đã được sử dụng.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Địa chỉ email này đã được đăng ký.");
        }

        Role userRole = roleRepository.findByName(Role.USER)
                .orElseThrow(() -> new IllegalStateException("Quyền USER không tồn tại trong hệ thống."));

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
                .orElseThrow(() -> new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác."));

        if (!user.isActive()) {
            throw new UnauthorizedException("Tài khoản này đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }
        if (!passwordHasher.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác.");
        }
        return tokenStore.issueToken(user.getId());
    }

    /**
     * Google OAuth 2.0 Single Sign-On:
     * - Decodes Google JWT ID Token if present.
     * - Finds existing user by email or auto-creates new account.
     * - Ensures Cart 1-1 is created.
     * - Issues secure bearer session token.
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu xác thực Google không hợp lệ.");
        }

        // Try decoding JWT credential if supplied by Google Identity Services
        if (request.getCredential() != null && !request.getCredential().isBlank()) {
            try {
                String[] parts = request.getCredential().split("\\.");
                if (parts.length >= 2) {
                    byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                    String json = new String(decoded, StandardCharsets.UTF_8);

                    String jwtEmail = extractJsonField(json, "email");
                    String jwtName = extractJsonField(json, "name");
                    String jwtSub = extractJsonField(json, "sub");
                    String jwtPicture = extractJsonField(json, "picture");

                    if (jwtEmail != null && !jwtEmail.isBlank() && (request.getEmail() == null || request.getEmail().isBlank())) {
                        request.setEmail(jwtEmail);
                    }
                    if (jwtName != null && !jwtName.isBlank() && (request.getName() == null || request.getName().isBlank())) {
                        request.setName(jwtName);
                    }
                    if (jwtSub != null && !jwtSub.isBlank() && (request.getGoogleId() == null || request.getGoogleId().isBlank())) {
                        request.setGoogleId(jwtSub);
                    }
                    if (jwtPicture != null && !jwtPicture.isBlank() && (request.getAvatar() == null || request.getAvatar().isBlank())) {
                        request.setAvatar(jwtPicture);
                    }
                }
            } catch (Exception ignored) {
                // Fall back to direct fields in request
            }
        }

        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new BadRequestException("Địa chỉ email tài khoản Google không hợp lệ.");
        }

        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        User user;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            if (!user.isActive()) {
                throw new UnauthorizedException("Tài khoản này đã bị tạm khóa. Vui lòng liên hệ hỗ trợ EcoGreen.");
            }
            cartService.getOrCreateCart(user);
        } else {
            // Auto-provision new user account
            String baseName = (request.getName() != null && !request.getName().isBlank())
                    ? request.getName()
                    : email.substring(0, email.indexOf('@'));
            String username = generateUniqueUsername(baseName);
            String randomPassword = UUID.randomUUID().toString();

            Role userRole = roleRepository.findByName(Role.USER)
                    .orElseThrow(() -> new IllegalStateException("USER role missing - run database init script."));

            User newUser = new User();
            newUser.setUsername(username);
            newUser.setEmail(email);
            newUser.setPassword(passwordHasher.hash(randomPassword));
            newUser.setRoles(new HashSet<>(java.util.List.of(userRole)));

            user = userRepository.save(newUser);
            cartService.createCartForUser(user);
        }

        String token = tokenStore.issueToken(user.getId());
        return new AuthResponse(token, UserResponse.from(user));
    }

    private String extractJsonField(String json, String field) {
        if (json == null) return null;
        try {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(.*?)(?<!\\\\)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                String val = matcher.group(1);
                return val.replace("\\\"", "\"").replace("\\\\", "\\");
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String generateUniqueUsername(String preferred) {
        if (preferred == null || preferred.isBlank()) {
            preferred = "ecouser";
        }
        String normalized = java.text.Normalizer.normalize(preferred, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9_]", "")
                .toLowerCase();
        if (normalized.isBlank()) {
            normalized = "ecouser";
        }
        if (normalized.length() > 35) {
            normalized = normalized.substring(0, 35);
        }
        String candidate = normalized;
        int counter = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = normalized + "_" + counter;
            counter++;
        }
        return candidate;
    }

    public void logout(String token) {
        tokenStore.revoke(token);
    }
}
