package com.example.backend.service;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.GoogleAuthRequest;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.GoogleLoginUnavailableException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtService;
import com.example.backend.security.RefreshTokenService;
import com.example.backend.security.GoogleIdentity;
import com.example.backend.security.GoogleIdentityVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B01-P1: Google login must trust ONLY a cryptographically verified ID token. No network: verifier is mocked. */
@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CartService cartService;
    @Mock private GoogleIdentityVerifier googleVerifier;

    @InjectMocks private AuthService authService;

    private static GoogleAuthRequest tokenRequest() {
        return new GoogleAuthRequest("header.payload.signature");
    }

    private static User userWith(Long id, String username, String email, String googleSub) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword("hash");
        u.setGoogleSub(googleSub);
        u.setRoles(new HashSet<>(List.of(new Role(1L, Role.USER))));
        return u;
    }

    private void configured() {
        when(googleVerifier.isConfigured()).thenReturn(true);
    }

    @Test
    void loginWithGoogle_forgedOrUnverifiableToken_throwsUnauthorized() {
        configured();
        when(googleVerifier.verify(anyString())).thenThrow(new UnauthorizedException("invalid"));

        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(tokenRequest()));

        verify(jwtService, never()).issueAccessToken(any());
        verify(refreshTokenService, never()).issueNewFamily(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_wrongAudience_throwsUnauthorized() {
        // Audience/issuer/expiry/signature are enforced by the verifier; a token for another client_id is rejected there.
        configured();
        when(googleVerifier.verify(anyString()))
                .thenThrow(new UnauthorizedException("Mã xác thực Google không hợp lệ hoặc đã hết hạn."));

        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(tokenRequest()));

        verify(jwtService, never()).issueAccessToken(any());
        verify(refreshTokenService, never()).issueNewFamily(any());
    }

    @Test
    void loginWithGoogle_emailNotVerified_throwsUnauthorized() {
        configured();
        when(googleVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("sub-1", "victim@example.com", false, "Victim", null));

        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(tokenRequest()));

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).issueAccessToken(any());
        verify(refreshTokenService, never()).issueNewFamily(any());
    }

    /** The original exploit: POST /api/auth/google {"email":"admin@ecogreen.vn"} with no token. */
    @Test
    void loginWithGoogle_requestWithOnlyEmailField_isRejected() {
        // The DTO has no email/name/googleId/avatar/credential property, so such JSON fields cannot reach the service.
        Set<String> fields = Arrays.stream(GoogleAuthRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
        assertEquals(Set.of("idToken"), fields);

        // What the server sees for the exploit body: idToken == null.
        GoogleAuthRequest exploit = new GoogleAuthRequest();

        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(exploit));
        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(new GoogleAuthRequest("  ")));
        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(null));

        verifyNoInteractions(userRepository, jwtService, refreshTokenService, cartService);
    }

    @Test
    void loginWithGoogle_validToken_existingUserByEmail_linksSubAndIssuesSession() {
        configured();
        when(googleVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("sub-123", "Alice@Example.com", true, "Alice", null));
        User existing = userWith(7L, "alice", "alice@example.com", null);
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issueAccessToken(existing)).thenReturn("access-token");
        when(refreshTokenService.issueNewFamily(existing)).thenReturn("refresh-token");

        AuthResponse response = authService.loginWithGoogle(tokenRequest());

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(7L, response.user.id);
        assertEquals("sub-123", existing.getGoogleSub());
        verify(userRepository).save(existing);
        verify(cartService).getOrCreateCart(existing);
    }

    @Test
    void loginWithGoogle_validToken_newUser_createsUserAndCart() {
        configured();
        when(googleVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("sub-new", "new.user@example.com", true, "New User", "http://pic"));
        when(userRepository.findByGoogleSub("sub-new")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new.user@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(roleRepository.findByName(Role.USER)).thenReturn(Optional.of(new Role(1L, Role.USER)));
        when(passwordHasher.hash(anyString())).thenReturn("random-bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(jwtService.issueAccessToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.issueNewFamily(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.loginWithGoogle(tokenRequest());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User created = saved.getValue();
        assertEquals("new.user@example.com", created.getEmail());
        assertEquals("sub-new", created.getGoogleSub());
        assertEquals("random-bcrypt-hash", created.getPassword());
        assertTrue(created.hasRole(Role.USER));
        assertFalse(created.hasRole(Role.ADMIN));
        assertNotNull(created.getUsername());
        verify(passwordHasher).hash(anyString()); // random, unusable password
        verify(cartService).createCartForUser(created);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
    }

    @Test
    void loginWithGoogle_subMismatch_throwsUnauthorized() {
        configured();
        when(googleVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("sub-attacker", "bob@example.com", true, "Bob", null));
        User existing = userWith(9L, "bob", "bob@example.com", "sub-original");
        when(userRepository.findByGoogleSub("sub-attacker")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(existing));

        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(tokenRequest()));

        assertEquals("sub-original", existing.getGoogleSub());
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).issueAccessToken(any());
        verify(refreshTokenService, never()).issueNewFamily(any());
    }

    @Test
    void loginWithGoogle_clientIdNotConfigured_failsClosed() {
        when(googleVerifier.isConfigured()).thenReturn(false);

        assertThrows(GoogleLoginUnavailableException.class, () -> authService.loginWithGoogle(tokenRequest()));

        verify(googleVerifier, never()).verify(anyString());
        verifyNoInteractions(userRepository, jwtService, refreshTokenService);
    }

    @Test
    void loginWithGoogle_deactivatedUser_throwsUnauthorized() {
        configured();
        lenient().when(googleVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("sub-x", "locked@example.com", true, "Locked", null));
        User locked = userWith(3L, "locked", "locked@example.com", "sub-x");
        locked.setActive(false);
        when(userRepository.findByGoogleSub("sub-x")).thenReturn(Optional.of(locked));

        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(tokenRequest()));

        verify(jwtService, never()).issueAccessToken(any());
        verify(refreshTokenService, never()).issueNewFamily(any());
    }
}
