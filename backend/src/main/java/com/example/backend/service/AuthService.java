package com.example.backend.service;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.GoogleAuthRequest;
import com.example.backend.dto.TokenPairResponse;
import com.example.backend.dto.UserResponse;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.GoogleLoginUnavailableException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtService;
import com.example.backend.security.LoginThrottle;
import com.example.backend.security.RefreshTokenService;
import com.example.backend.security.GoogleIdentity;
import com.example.backend.security.GoogleIdentityVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final String GOOGLE_AUTH_FAILED = "Xác thực Google không thành công.";
    private static final String BAD_CREDENTIALS = "Tên đăng nhập hoặc mật khẩu không chính xác.";

    /** Valid BCrypt hash of a random value, built once; lets login spend the same time for unknown usernames. */
    private volatile String dummyHash;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private JwtService jwtService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private LoginThrottle loginThrottle;
    @Autowired private CartService cartService;
    @Autowired private GoogleIdentityVerifier googleVerifier;

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

    /**
     * Username/password login. One user lookup; unknown username and wrong password are indistinguishable (same message,
     * and a BCrypt comparison is always performed so timing does not reveal which usernames exist). The "locked"
     * message is only revealed after the correct password. Failed attempts are throttled per username + client IP.
     */
    public AuthResponse login(String username, String password, String clientIp) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        loginThrottle.checkAllowed(username, clientIp);

        User user = userRepository.findByUsername(username).orElse(null);
        boolean passwordOk = passwordHasher.matches(password, user != null ? user.getPassword() : dummyHash());
        if (user == null || !passwordOk) {
            loginThrottle.recordFailure(username, clientIp);
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new UnauthorizedException("Tài khoản này đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }
        loginThrottle.recordSuccess(username, clientIp);
        return issueSession(user);
    }

    /** Rotates the refresh token (reuse of a revoked one revokes the whole family) and issues a new access token. */
    public TokenPairResponse refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
        return new TokenPairResponse(jwtService.issueAccessToken(rotated.user()), rotated.refreshToken(),
                jwtService.getAccessTtlSeconds());
    }

    /** Revokes exactly the given refresh token; the access token simply expires. */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse issueSession(User user) {
        return new AuthResponse(jwtService.issueAccessToken(user), refreshTokenService.issueNewFamily(user),
                jwtService.getAccessTtlSeconds(), UserResponse.from(user));
    }

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = passwordHasher.hash(UUID.randomUUID().toString());
            dummyHash = h;
        }
        return h;
    }

    /**
     * Google Sign-In (ID-token flow). The ONLY client input is the ID token; email/name/sub come from the
     * cryptographically verified token payload. Matching: google_sub first, then verified email (linking
     * google_sub). A different existing google_sub is rejected. Fails closed when Google login is not configured.
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        String idToken = request == null ? null : request.getIdToken();
        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedException(GOOGLE_AUTH_FAILED);
        }
        if (!googleVerifier.isConfigured()) {
            throw new GoogleLoginUnavailableException("Đăng nhập bằng Google hiện chưa được cấu hình.");
        }

        GoogleIdentity identity = googleVerifier.verify(idToken); // throws if forged/expired/wrong aud/iss
        if (identity == null || !identity.emailVerified()) {
            throw new UnauthorizedException("Email của tài khoản Google chưa được xác minh.");
        }
        String sub = identity.sub() == null ? "" : identity.sub().trim();
        String email = identity.email() == null ? "" : identity.email().trim().toLowerCase();
        if (sub.isEmpty() || email.isEmpty() || !email.contains("@")) {
            throw new UnauthorizedException(GOOGLE_AUTH_FAILED);
        }

        User user;
        Optional<User> bySub = userRepository.findByGoogleSub(sub);
        if (bySub.isPresent()) {
            user = bySub.get();
            requireActive(user);
            cartService.getOrCreateCart(user);
        } else {
            Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
            if (byEmail.isPresent()) {
                user = byEmail.get();
                if (user.getGoogleSub() != null && !user.getGoogleSub().equals(sub)) {
                    throw new UnauthorizedException(GOOGLE_AUTH_FAILED);
                }
                requireActive(user);
                if (user.getGoogleSub() == null) {
                    user.setGoogleSub(sub);
                    user = userRepository.save(user);
                }
                cartService.getOrCreateCart(user);
            } else {
                String baseName = (identity.name() != null && !identity.name().isBlank())
                        ? identity.name()
                        : email.substring(0, email.indexOf('@'));
                Role userRole = roleRepository.findByName(Role.USER)
                        .orElseThrow(() -> new IllegalStateException("USER role missing - run database init script."));

                User newUser = new User();
                newUser.setUsername(generateUniqueUsername(baseName));
                newUser.setEmail(email);
                // Random, never disclosed: the account can only be entered through Google (or a later reset).
                newUser.setPassword(passwordHasher.hash(UUID.randomUUID() + UUID.randomUUID().toString()));
                newUser.setGoogleSub(sub);
                newUser.setRoles(new HashSet<>(java.util.List.of(userRole)));

                user = userRepository.save(newUser);
                cartService.createCartForUser(user);
            }
        }

        return issueSession(user);
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new UnauthorizedException("Tài khoản này đã bị tạm khóa. Vui lòng liên hệ hỗ trợ EcoGreen.");
        }
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

}
