package com.example.backend.service;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.FacebookAuthRequest;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.FacebookLoginUnavailableException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.FacebookIdentity;
import com.example.backend.security.FacebookIdentityVerifier;
import com.example.backend.security.JwtService;
import com.example.backend.security.RefreshTokenService;
import com.example.backend.security.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B01: Facebook login orchestration. Policy under test: look up by facebook_id, otherwise create; NEVER link by e-mail. */
@ExtendWith(MockitoExtension.class)
class AuthServiceFacebookLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CartService cartService;
    @Mock private FacebookIdentityVerifier facebookVerifier;
    @Spy private JwtService jwtService = new JwtService(TestJwt.SECRET, 900);

    @InjectMocks private AuthService authService;

    private static FacebookAuthRequest token() {
        return new FacebookAuthRequest("fb-access-token");
    }

    private static User existing(boolean active) {
        User u = new User();
        u.setId(21L);
        u.setUsername("fbuser");
        u.setEmail("fb@example.com");
        u.setFacebookId("fb-1");
        u.setActive(active);
        u.setRoles(new HashSet<>(Set.of(new Role(1L, Role.CUSTOMER))));
        return u;
    }

    @Test
    void notConfigured_failsClosed_withoutCallingFacebook() {
        when(facebookVerifier.isConfigured()).thenReturn(false);
        assertThrows(FacebookLoginUnavailableException.class, () -> authService.loginWithFacebook(token()));
        verify(facebookVerifier, never()).verify(any());
    }

    @Test
    void blankToken_isUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> authService.loginWithFacebook(new FacebookAuthRequest("  ")));
        assertThrows(UnauthorizedException.class, () -> authService.loginWithFacebook(null));
    }

    @Test
    void knownFacebookId_logsIn_withoutCreatingAnything() {
        User u = existing(true);
        when(facebookVerifier.isConfigured()).thenReturn(true);
        when(facebookVerifier.verify("fb-access-token")).thenReturn(new FacebookIdentity("fb-1", "Fb User", "fb@example.com"));
        when(userRepository.findByFacebookId("fb-1")).thenReturn(Optional.of(u));
        when(refreshTokenService.issueNewFamily(u)).thenReturn("refresh-raw");

        AuthResponse response = authService.loginWithFacebook(token());

        assertEquals("refresh-raw", response.refreshToken());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void knownFacebookId_butDeactivatedAccount_isRejected() {
        when(facebookVerifier.isConfigured()).thenReturn(true);
        when(facebookVerifier.verify("fb-access-token")).thenReturn(new FacebookIdentity("fb-1", "Fb User", "fb@example.com"));
        when(userRepository.findByFacebookId("fb-1")).thenReturn(Optional.of(existing(false)));

        assertThrows(UnauthorizedException.class, () -> authService.loginWithFacebook(token()));
        verify(refreshTokenService, never()).issueNewFamily(any());
    }

    @Test
    void newFacebookUser_withEmail_createsCustomerAccount_andCart() {
        when(facebookVerifier.isConfigured()).thenReturn(true);
        when(facebookVerifier.verify("fb-access-token")).thenReturn(new FacebookIdentity("fb-9", "Nguyễn Văn An", "An@Example.com"));
        when(userRepository.findByFacebookId("fb-9")).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("an@example.com")).thenReturn(false);
        when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.of(new Role(1L, Role.CUSTOMER)));
        when(passwordHasher.hash(any())).thenReturn("random-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenService.issueNewFamily(any(User.class))).thenReturn("refresh-raw");

        authService.loginWithFacebook(token());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals("fb-9", saved.getValue().getFacebookId());
        assertEquals("an@example.com", saved.getValue().getEmail());
        assertEquals("nguyenvanan", saved.getValue().getUsername());
        assertNull(saved.getValue().getGoogleSub());
        assertTrue(saved.getValue().hasRole(Role.CUSTOMER));
        verify(cartService).createCartForUser(saved.getValue());
    }

    @Test
    void newFacebookUser_withoutEmail_isBadRequest() {
        when(facebookVerifier.isConfigured()).thenReturn(true);
        when(facebookVerifier.verify("fb-access-token")).thenReturn(new FacebookIdentity("fb-9", "No Mail", null));
        when(userRepository.findByFacebookId("fb-9")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> authService.loginWithFacebook(token()));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void emailAlreadyBelongsToAnotherAccount_isConflict_neverLinked() {
        when(facebookVerifier.isConfigured()).thenReturn(true);
        when(facebookVerifier.verify("fb-access-token")).thenReturn(new FacebookIdentity("fb-9", "Victim Twin", "victim@example.com"));
        when(userRepository.findByFacebookId("fb-9")).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("victim@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.loginWithFacebook(token()));
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(userRepository, never()).save(any(User.class));
    }
}
