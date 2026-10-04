package com.example.backend.security;

import com.example.backend.entity.RefreshToken;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private InMemoryRefreshTokens store;
    private UserRepository users;
    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        store = new InMemoryRefreshTokens();
        users = mock(UserRepository.class);
        user = new User();
        user.setId(5L);
        user.setUsername("alice");
        user.setRoles(new HashSet<>(Set.of(new Role(1L, Role.USER))));
        when(users.findById(5L)).thenReturn(Optional.of(user));
        service = new RefreshTokenService(store.repository, users, 14, Clock.systemUTC());
    }

    @Test
    void issue_storesOnlySha256Hash_andRawTokenIsLongAndUrlSafe() {
        String raw = service.issueNewFamily(user);

        assertTrue(raw.length() >= 86, "64 random bytes encode to >= 86 URL-safe chars");
        assertTrue(raw.matches("[A-Za-z0-9_-]+"));
        RefreshToken row = store.byRaw(raw).orElseThrow();
        assertEquals(64, row.getTokenHash().length());
        assertNotEquals(raw, row.getTokenHash());
        assertFalse(store.rows.values().stream().anyMatch(t -> t.getTokenHash().equals(raw)), "raw token must not be stored");
        assertFalse(row.isRevoked());
        assertEquals(5L, row.getUserId());
    }

    @Test
    void refresh_validToken_rotatesAndRevokesOld() {
        String first = service.issueNewFamily(user);

        RefreshTokenService.Rotated rotated = service.rotate(first);

        assertNotEquals(first, rotated.refreshToken());
        assertEquals(user, rotated.user());
        RefreshToken old = store.byRaw(first).orElseThrow();
        RefreshToken next = store.byRaw(rotated.refreshToken()).orElseThrow();
        assertTrue(old.isRevoked());
        assertFalse(next.isRevoked());
        assertEquals(old.getFamilyId(), next.getFamilyId());
        assertEquals(next.getId(), old.getReplacedById());
    }

    @Test
    void refresh_revokedToken_revokesWholeFamilyAndReturns401() {
        String first = service.issueNewFamily(user);
        String second = service.rotate(first).refreshToken(); // first is now revoked, second is the live token

        assertThrows(UnauthorizedException.class, () -> service.rotate(first)); // reuse of the rotated token

        assertTrue(store.byRaw(second).orElseThrow().isRevoked(), "the live token of the family must be revoked too");
        assertThrows(UnauthorizedException.class, () -> service.rotate(second));
    }

    @Test
    void refresh_expiredToken_returns401() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofDays(30)), ZoneOffset.UTC);
        String raw = new RefreshTokenService(store.repository, users, 14, past).issueNewFamily(user);

        assertThrows(UnauthorizedException.class, () -> service.rotate(raw));
    }

    @Test
    void refresh_unknownOrBlankOrOversizedToken_returns401() {
        assertThrows(UnauthorizedException.class, () -> service.rotate("not-a-real-token"));
        assertThrows(UnauthorizedException.class, () -> service.rotate(""));
        assertThrows(UnauthorizedException.class, () -> service.rotate(null));
        assertThrows(UnauthorizedException.class, () -> service.rotate("x".repeat(5000)));
    }

    @Test
    void refresh_deactivatedUser_returns401AndRevokesFamily() {
        String raw = service.issueNewFamily(user);
        user.setActive(false);

        assertThrows(UnauthorizedException.class, () -> service.rotate(raw));

        assertTrue(store.byRaw(raw).orElseThrow().isRevoked());
        assertTrue(store.rows.values().stream().allMatch(RefreshToken::isRevoked));
    }

    @Test
    void logout_revokesRefreshToken() {
        String raw = service.issueNewFamily(user);

        service.revoke(raw);

        assertTrue(store.byRaw(raw).orElseThrow().isRevoked());
        assertThrows(UnauthorizedException.class, () -> service.rotate(raw));
        service.revoke(raw);              // idempotent
        service.revoke("unknown-token");  // unknown tokens are ignored
        service.revoke(null);
        assertNotNull(store.byRaw(raw));
    }
}
