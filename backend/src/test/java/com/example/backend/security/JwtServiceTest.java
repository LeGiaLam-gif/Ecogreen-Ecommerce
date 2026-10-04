package com.example.backend.security;

import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static User user(long id, String... roleNames) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        Set<Role> roles = new HashSet<>();
        long rid = 1;
        for (String r : roleNames) roles.add(new Role(rid++, r));
        u.setRoles(roles);
        return u;
    }

    private final JwtService jwt = new JwtService(TestJwt.SECRET, 900);

    @Test
    void issue_validToken_containsSubRolesPermissionsIatExpJti_andParsesBack() {
        String token = jwt.issueAccessToken(user(7, Role.USER, Role.ADMIN));

        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(TestJwt.SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertEquals("7", claims.getSubject());
        assertEquals(List.of("ADMIN", "USER"), claims.get("roles"));
        assertEquals(List.of(), claims.get("permissions"));
        assertTrue(claims.getId() != null && !claims.getId().isBlank());
        assertEquals(900_000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime(), 1000);

        CurrentUser current = jwt.parse(token);
        assertEquals(7L, current.getUserId());
        assertTrue(current.isAdmin());
        assertEquals(List.of("ADMIN", "USER"), current.getRoles());
        assertEquals(List.of(), current.getPermissions());
    }

    @Test
    void jwt_expiredToken_isRejected() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String expired = new JwtService(TestJwt.SECRET, 900, past).issueAccessToken(user(1, Role.USER));

        assertThrows(JwtException.class, () -> jwt.parse(expired));
    }

    @Test
    void jwt_tamperedSignature_isRejected() {
        String token = jwt.issueAccessToken(user(1, Role.USER));
        String[] parts = token.split("\\.");
        String sig = parts[2];
        String flipped = sig.substring(0, sig.length() - 2) + (sig.endsWith("AA") ? "BB" : "AA");

        assertThrows(JwtException.class, () -> jwt.parse(parts[0] + "." + parts[1] + "." + flipped));
    }

    @Test
    void jwt_tamperedPayload_isRejected() {
        String token = jwt.issueAccessToken(user(1, Role.USER));
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"roles\":[\"ADMIN\"],\"permissions\":[]}".getBytes(StandardCharsets.UTF_8));

        assertThrows(JwtException.class, () -> jwt.parse(parts[0] + "." + forgedPayload + "." + parts[2]));
    }

    @Test
    void jwt_signedWithOtherKey_unsignedAndLegacyUuidTokens_areRejected() {
        String otherKeyToken = new JwtService("another-secret-another-secret-another-secret-123456", 900)
                .issueAccessToken(user(1, Role.USER));
        String unsigned = Jwts.builder().subject("1").claim("roles", List.of("ADMIN")).compact();

        assertThrows(JwtException.class, () -> jwt.parse(otherKeyToken));
        assertThrows(JwtException.class, () -> jwt.parse(unsigned));
        assertThrows(RuntimeException.class, () -> jwt.parse("3f2b8c1e-5a4d-4e0b-9a52-0c3d6f7e8a91")); // legacy UUID session
        assertThrows(RuntimeException.class, () -> jwt.parse(""));
    }

    @Test
    void jwt_missingOrShortSecret_failsFastAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 900));
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 900));
        IllegalStateException tooShort = assertThrows(IllegalStateException.class, () -> new JwtService("short-secret", 900));
        assertTrue(tooShort.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void eachToken_hasUniqueJti() {
        User u = user(1, Role.USER);
        assertNotEquals(jwt.issueAccessToken(u), jwt.issueAccessToken(u));
    }
}
