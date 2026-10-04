package com.example.backend.security;

import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies signed (HS256) access tokens. Claims: sub = user id, roles, permissions (empty until B01-P3),
 * iat, exp, jti. The secret comes from JWT_SECRET; the application FAILS TO START when it is missing or shorter
 * than 32 bytes. Tokens/jti are never logged.
 */
@Component
public class JwtService {

    public static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${ecogreen.jwt.secret:}") String secret,
                      @Value("${ecogreen.jwt.access-ttl-seconds:900}") long accessTtlSeconds) {
        this(secret, accessTtlSeconds, Clock.systemUTC());
    }

    public JwtService(String secret, long accessTtlSeconds, Clock clock) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET is missing or too short: set the JWT_SECRET environment variable "
                    + "to at least " + MIN_SECRET_BYTES + " bytes of random data (e.g. `openssl rand -base64 48`).");
        }
        if (accessTtlSeconds <= 0) {
            throw new IllegalStateException("ecogreen.jwt.access-ttl-seconds must be positive.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtlSeconds = accessTtlSeconds;
        this.clock = clock;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public String issueAccessToken(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).sorted().toList();
        Date now = Date.from(clock.instant());
        Date exp = Date.from(clock.instant().plusSeconds(accessTtlSeconds));
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("roles", roles)
                .claim("permissions", List.of()) // populated by B01-P3
                .issuedAt(now)
                .expiration(exp)
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies signature and expiry and returns the identity in the token.
     * @throws JwtException (or IllegalArgumentException) when the token is malformed, tampered, unsigned or expired
     */
    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        return new CurrentUser(userId, stringList(claims.get("roles")), stringList(claims.get("permissions")));
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
}
