package com.example.backend.security;

import com.example.backend.entity.RefreshToken;
import com.example.backend.entity.User;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.RefreshTokenRepository;
import com.example.backend.repository.UserRepository;
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
import java.util.UUID;

/**
 * Opaque refresh tokens: 64 random bytes (URL-safe Base64), returned once, stored only as hex SHA-256.
 * Each use ROTATES the token (old one revoked, new one in the same family). Presenting an already-revoked token
 * is treated as theft: the whole family is revoked and the request is rejected with 401.
 * The raw token and its hash are never logged.
 */
@Service
public class RefreshTokenService {

    static final String INVALID = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.";
    private static final int MAX_TOKEN_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Rotated(String refreshToken, User user) {}

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final long ttlDays;
    private final Clock clock;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users,
                               @Value("${ecogreen.jwt.refresh-ttl-days:14}") long ttlDays) {
        this(tokens, users, ttlDays, Clock.systemUTC());
    }

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, long ttlDays, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.ttlDays = ttlDays;
        this.clock = clock;
    }

    /** Starts a new token family (login). Returns the raw token - the only time it is ever visible. */
    @Transactional
    public String issueNewFamily(User user) {
        return create(user.getId(), UUID.randomUUID().toString()).raw();
    }

    /** Validates + rotates. noRollbackFor: the family revocation on reuse must be committed even though we throw. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotated rotate(String rawToken) {
        RefreshToken current = lookup(rawToken);

        if (current.isRevoked()) { // reuse of an already-rotated/revoked token
            tokens.revokeFamily(current.getFamilyId());
            throw new UnauthorizedException(INVALID);
        }
        if (current.getExpiresAt().isBefore(now())) {
            throw new UnauthorizedException(INVALID);
        }
        // Atomically claim the token; if another request already did, this is a concurrent reuse.
        if (tokens.revokeIfActive(current.getId()) == 0) {
            tokens.revokeFamily(current.getFamilyId());
            throw new UnauthorizedException(INVALID);
        }
        User user = users.findById(current.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            tokens.revokeFamily(current.getFamilyId());
            throw new UnauthorizedException(INVALID);
        }

        Created next = create(current.getUserId(), current.getFamilyId());
        tokens.markReplaced(current.getId(), next.id());
        return new Rotated(next.raw(), user);
    }

    /** Logout: revoke exactly this token. Idempotent; unknown/invalid tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) return;
        tokens.findByTokenHash(sha256Hex(rawToken)).ifPresent(t -> {
            if (!t.isRevoked()) tokens.revokeIfActive(t.getId());
        });
    }

    private RefreshToken lookup(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) {
            throw new UnauthorizedException(INVALID);
        }
        return tokens.findByTokenHash(sha256Hex(rawToken)).orElseThrow(() -> new UnauthorizedException(INVALID));
    }

    private record Created(Long id, String raw) {}

    private Created create(Long userId, String familyId) {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256Hex(raw));
        entity.setFamilyId(familyId);
        entity.setExpiresAt(now().plusDays(ttlDays));
        entity.setCreatedAt(now());
        RefreshToken saved = tokens.save(entity);
        return new Created(saved.getId(), raw);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock); // clock is UTC
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
