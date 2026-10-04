package com.example.backend.service;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.TokenPairResponse;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.JwtService;
import com.example.backend.security.LoginThrottle;
import com.example.backend.security.RefreshTokenService;
import com.example.backend.security.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B01-P2: username/password login, refresh and logout orchestration in AuthService. */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    private static final String BAD = "Tên đăng nhập hoặc mật khẩu không chính xác.";

    @Mock private UserRepository userRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private RefreshTokenService refreshTokenService;
    @Spy private JwtService jwtService = new JwtService(TestJwt.SECRET, 900);
    @Spy private LoginThrottle loginThrottle = new LoginThrottle(3, 900, java.time.Clock.systemUTC());

    @InjectMocks private AuthService authService;

    private static User user(boolean active) {
        User u = new User();
        u.setId(11L);
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setPassword("bcrypt-hash");
        u.setActive(active);
        u.setRoles(new HashSet<>(Set.of(new Role(1L, Role.CUSTOMER), new Role(2L, Role.ADMIN))));
        return u;
    }

    @Test
    void login_correctPassword_returnsValidJwtWithClaims() {
        User u = user(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        when(passwordHasher.matches("secret", "bcrypt-hash")).thenReturn(true);
        when(refreshTokenService.issueNewFamily(u)).thenReturn("refresh-raw");

        AuthResponse response = authService.login("alice", "secret", "1.2.3.4");

        CurrentUser parsed = jwtService.parse(response.accessToken()); // signature + expiry verified
        assertEquals(11L, parsed.getUserId());
        assertEquals(List.of("ADMIN", "CUSTOMER"), parsed.getRoles());
        assertEquals(List.of(), parsed.getPermissions());
        assertEquals("refresh-raw", response.refreshToken());
        assertEquals(900L, response.expiresIn());
        assertEquals("alice", response.user().username);
        verify(userRepository, times(1)).findByUsername(anyString()); // user is queried exactly once
    }

    @Test
    void login_wrongPassword_throwsUnauthorizedWithoutUserEnumeration() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(true)));
        when(passwordHasher.matches("wrong", "bcrypt-hash")).thenReturn(false);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(passwordHasher.hash(anyString())).thenReturn("dummy-bcrypt-hash");
        when(passwordHasher.matches("whatever", "dummy-bcrypt-hash")).thenReturn(false);

        UnauthorizedException wrongPassword =
                assertThrows(UnauthorizedException.class, () -> authService.login("alice", "wrong", "1.2.3.4"));
        UnauthorizedException unknownUser =
                assertThrows(UnauthorizedException.class, () -> authService.login("ghost", "whatever", "1.2.3.4"));

        assertEquals(BAD, wrongPassword.getMessage());
        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage()); // identical => no enumeration
        verify(passwordHasher).matches("whatever", "dummy-bcrypt-hash");     // a BCrypt comparison also ran for the unknown user
        verify(refreshTokenService, never()).issueNewFamily(any());
    }

    @Test
    void login_deactivatedUser_isRejected_andLockedMessageOnlyAfterCorrectPassword() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(false)));
        when(passwordHasher.matches("right", "bcrypt-hash")).thenReturn(true);
        when(passwordHasher.matches("wrong", "bcrypt-hash")).thenReturn(false);

        UnauthorizedException withWrong =
                assertThrows(UnauthorizedException.class, () -> authService.login("bob", "wrong", "1.2.3.4"));
        UnauthorizedException withRight =
                assertThrows(UnauthorizedException.class, () -> authService.login("bob", "right", "1.2.3.4"));

        assertEquals(BAD, withWrong.getMessage());
        assertTrue(withRight.getMessage().contains("khóa"));
        verify(refreshTokenService, never()).issueNewFamily(any());
    }

    @Test
    void login_repeatedFailures_getRateLimited_evenWithCorrectPasswordAfterwards() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(true)));
        when(passwordHasher.matches("wrong", "bcrypt-hash")).thenReturn(false);

        for (int i = 0; i < 3; i++) {
            assertThrows(UnauthorizedException.class, () -> authService.login("alice", "wrong", "9.9.9.9"));
        }
        LoginThrottle.TooManyAttemptsException locked = assertThrows(LoginThrottle.TooManyAttemptsException.class,
                () -> authService.login("alice", "wrong", "9.9.9.9"));

        assertTrue(locked.getRetryAfterSeconds() > 0);
        verify(userRepository, times(3)).findByUsername(anyString()); // 4th attempt rejected before any lookup/BCrypt
    }

    @Test
    void login_blankCredentials_areRejectedAsBadCredentials() {
        assertThrows(UnauthorizedException.class, () -> authService.login(null, "x", "ip"));
        assertThrows(UnauthorizedException.class, () -> authService.login("alice", "", "ip"));
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void refresh_issuesNewAccessTokenAndRotatedRefreshToken() {
        User u = user(true);
        when(refreshTokenService.rotate("old-raw")).thenReturn(new RefreshTokenService.Rotated("new-raw", u));

        TokenPairResponse pair = authService.refresh("old-raw");

        assertEquals("new-raw", pair.refreshToken());
        assertEquals(11L, jwtService.parse(pair.accessToken()).getUserId());
        assertEquals(900L, pair.expiresIn());
    }

    @Test
    void logout_revokesTheGivenRefreshToken() {
        authService.logout("raw-refresh");

        verify(refreshTokenService).revoke(eq("raw-refresh"));
    }
}
