package com.example.backend.service;

import com.example.backend.entity.PasswordResetToken;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.PasswordResetTokenRepository;
import com.example.backend.repository.RefreshTokenRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.PasswordResetThrottle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Forgot / reset password. The token is 32 random bytes (URL-safe Base64), mailed once and stored only as hex SHA-256; it
 * lives {@code ecogreen.auth.password-reset-ttl-minutes} and works once. Requesting a reset never reveals whether the address
 * has an account. A finished reset replaces the password hash and revokes every refresh token of the user, so a stolen session
 * does not survive it. Access tokens already issued live out their (15 minute) lifetime.
 */
@Service
public class PasswordResetService {

    static final String INVALID_LINK = "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu liên kết mới.";
    private static final String DEFAULT_PUBLIC_URL = "http://localhost:5173";
    private static final int MAX_TOKEN_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final AuthMailService mail;
    private final PasswordResetThrottle throttle;
    private final long ttlMinutes;
    private final String publicUrl;
    private final Clock clock;

    @Autowired
    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
                                RefreshTokenRepository refreshTokens, PasswordHasher passwordHasher,
                                AuthMailService mail, PasswordResetThrottle throttle,
                                @Value("${ecogreen.auth.password-reset-ttl-minutes:30}") long ttlMinutes,
                                @Value("${ecogreen.app.public-url:" + DEFAULT_PUBLIC_URL + "}") String publicUrl) {
        this(tokens, users, refreshTokens, passwordHasher, mail, throttle, ttlMinutes, publicUrl, Clock.systemUTC());
    }

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
                                RefreshTokenRepository refreshTokens, PasswordHasher passwordHasher,
                                AuthMailService mail, PasswordResetThrottle throttle,
                                long ttlMinutes, String publicUrl, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordHasher = passwordHasher;
        this.mail = mail;
        this.throttle = throttle;
        this.ttlMinutes = ttlMinutes;
        this.publicUrl = publicUrl;
        this.clock = clock;
    }

    /**
     * Always returns normally for a well-formed request, whether or not the address has an (active) account.
     * @throws PasswordResetThrottle.TooManyRequestsException when this IP or address asks too often
     */
    @Transactional
    public void requestReset(String email, String clientIp) {
        throttle.checkAndRecord(clientIp, email);
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return;

        Optional<User> found = users.findByEmailIgnoreCase(normalized);
        if (found.isEmpty() || !found.get().isActive()) return; // same outward behaviour as a real account
        User user = found.get();

        LocalDateTime now = LocalDateTime.now(clock);
        tokens.invalidateOpenForUser(user.getId(), now); // only the newest link works

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        PasswordResetToken row = new PasswordResetToken();
        row.setUserId(user.getId());
        row.setTokenHash(sha256Hex(token));
        row.setCreatedAt(now);
        row.setExpiresAt(now.plusMinutes(ttlMinutes));
        tokens.save(row);

        mail.sendPasswordReset(user.getId(), user.getEmail(), buildLink(token), ttlMinutes);
    }

    /** @throws BadRequestException when the password is weak or the link is unknown, used or expired */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordPolicy.validate(newPassword); // before the claim: a weak password must not burn the link
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new BadRequestException(INVALID_LINK);
        }
        PasswordResetToken row = tokens.findByTokenHash(sha256Hex(token.trim()))
                .orElseThrow(() -> new BadRequestException(INVALID_LINK));

        LocalDateTime now = LocalDateTime.now(clock);
        if (tokens.claim(row.getId(), now) != 1) { // atomic: two concurrent uses cannot both win
            throw new BadRequestException(INVALID_LINK);
        }
        User user = users.findById(row.getUserId()).orElseThrow(() -> new BadRequestException(INVALID_LINK));
        if (!user.isActive()) {
            throw new BadRequestException(INVALID_LINK);
        }
        user.setPassword(passwordHasher.hash(newPassword));
        users.save(user);
        tokens.invalidateOpenForUser(user.getId(), now);
        refreshTokens.revokeAllForUser(user.getId());
    }

    private String buildLink(String token) {
        String base = (publicUrl == null || publicUrl.isBlank()) ? DEFAULT_PUBLIC_URL : publicUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/reset-password?token=" + token;
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
