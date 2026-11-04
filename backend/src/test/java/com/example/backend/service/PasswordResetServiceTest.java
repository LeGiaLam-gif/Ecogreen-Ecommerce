package com.example.backend.service;

import com.example.backend.entity.PasswordResetToken;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.repository.PasswordResetTokenRepository;
import com.example.backend.repository.RefreshTokenRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.PasswordResetThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B01: forgot / reset password. Mail and throttle are mocked; the clock is fixed so expiry is exact. */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 10, 0);

    @Mock private PasswordResetTokenRepository tokens;
    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordHasher passwordHasher;
    @Mock private AuthMailService mail;
    @Mock private PasswordResetThrottle throttle;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokens, users, refreshTokens, passwordHasher, mail, throttle, 30,
                "https://shop.example.com/", CLOCK);
    }

    private static User user(boolean active) {
        User u = new User();
        u.setId(5L);
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setPassword("old-hash");
        u.setActive(active);
        return u;
    }

    private static PasswordResetToken row(long id, long userId) throws Exception {
        PasswordResetToken t = new PasswordResetToken();
        Field f = PasswordResetToken.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(t, id);
        t.setUserId(userId);
        return t;
    }

    @Test
    void request_forExistingUser_storesOnlyTheHash_andMailsTheLink() {
        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user(true)));

        service.requestReset("  Alice@Example.com ", "1.2.3.4");

        verify(throttle).checkAndRecord("1.2.3.4", "  Alice@Example.com ");
        verify(tokens).invalidateOpenForUser(5L, NOW);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordReset(eq(5L), eq("alice@example.com"), link.capture(), eq(30L));

        String prefix = "https://shop.example.com/reset-password?token=";
        assertTrue(link.getValue().startsWith(prefix), link.getValue());
        String rawToken = link.getValue().substring(prefix.length());
        assertEquals(43, rawToken.length()); // 32 bytes, URL-safe Base64 without padding
        assertEquals(PasswordResetService.sha256Hex(rawToken), saved.getValue().getTokenHash());
        assertNotEquals(rawToken, saved.getValue().getTokenHash());
        assertEquals(NOW.plusMinutes(30), saved.getValue().getExpiresAt());
        assertEquals(5L, saved.getValue().getUserId());
    }

    @Test
    void request_forUnknownOrDeactivatedAccount_looksIdentical_butSendsNothing() {
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
        service.requestReset("nobody@example.com", "1.2.3.4");

        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user(false)));
        service.requestReset("alice@example.com", "1.2.3.4");

        verify(tokens, never()).save(any());
        verifyNoInteractions(mail);
    }

    @Test
    void request_overTheLimit_isRejectedBeforeAnyLookup() {
        doThrow(new PasswordResetThrottle.TooManyRequestsException(60)).when(throttle).checkAndRecord(anyString(), anyString());

        assertThrows(PasswordResetThrottle.TooManyRequestsException.class, () -> service.requestReset("a@example.com", "1.2.3.4"));
        verifyNoInteractions(users, tokens, mail);
    }

    @Test
    void reset_withValidLink_changesPassword_endsAllSessions_andVoidsOtherLinks() throws Exception {
        User u = user(true);
        when(tokens.findByTokenHash(PasswordResetService.sha256Hex("good-token"))).thenReturn(Optional.of(row(9L, 5L)));
        when(tokens.claim(9L, NOW)).thenReturn(1);
        when(users.findById(5L)).thenReturn(Optional.of(u));
        when(passwordHasher.hash("newpassword1")).thenReturn("new-hash");

        service.resetPassword("good-token", "newpassword1");

        assertEquals("new-hash", u.getPassword());
        verify(users).save(u);
        verify(refreshTokens).revokeAllForUser(5L);
        verify(tokens).invalidateOpenForUser(5L, NOW);
    }

    @Test
    void reset_withUsedOrExpiredLink_isBadRequest_andChangesNothing() throws Exception {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(row(9L, 5L)));
        when(tokens.claim(9L, NOW)).thenReturn(0); // already used, or expired

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.resetPassword("old-token", "newpassword1"));
        assertEquals(PasswordResetService.INVALID_LINK, ex.getMessage());
        verifyNoInteractions(users, passwordHasher, refreshTokens);
    }

    @Test
    void reset_withUnknownLink_isBadRequest() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.resetPassword("made-up", "newpassword1"));
        verify(tokens, never()).claim(anyLong(), any());
    }

    @Test
    void reset_withWeakPassword_doesNotBurnTheLink() {
        assertThrows(BadRequestException.class, () -> service.resetPassword("good-token", "short"));
        verifyNoInteractions(tokens, users);
    }

    @Test
    void reset_forDeactivatedAccount_isRejected() throws Exception {
        User u = user(false);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(row(9L, 5L)));
        when(tokens.claim(9L, NOW)).thenReturn(1);
        when(users.findById(5L)).thenReturn(Optional.of(u));

        assertThrows(BadRequestException.class, () -> service.resetPassword("good-token", "newpassword1"));
        assertEquals("old-hash", u.getPassword());
        verifyNoInteractions(refreshTokens);
    }
}
