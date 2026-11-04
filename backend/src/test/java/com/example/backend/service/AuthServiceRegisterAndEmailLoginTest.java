package com.example.backend.service;

import com.example.backend.dto.AuthResponse;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B01: registration (normalisation, duplicates, password policy, race) and login with an e-mail address. */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterAndEmailLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CartService cartService;
    @Spy private JwtService jwtService = new JwtService(TestJwt.SECRET, 900);
    @Spy private LoginThrottle loginThrottle = new LoginThrottle(3, 900, java.time.Clock.systemUTC());

    @InjectMocks private AuthService authService;

    private void customerRoleExists() {
        when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.of(new Role(1L, Role.CUSTOMER)));
    }

    @Test
    void register_trimsUsername_lowercasesEmail_hashesPassword_andCreatesCart() {
        customerRoleExists();
        when(passwordHasher.hash("password1")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = authService.register("  alice ", " Alice@Example.COM ", "password1");

        assertEquals("alice", saved.getUsername());
        assertEquals("alice@example.com", saved.getEmail());
        assertEquals("hash", saved.getPassword());
        verify(userRepository).existsByUsernameIgnoreCase("alice");
        verify(userRepository).existsByEmailIgnoreCase("alice@example.com");
        verify(cartService).createCartForUser(saved);
    }

    @Test
    void register_duplicateUsernameOrEmail_isConflict_andNothingIsSaved() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(true);
        assertThrows(ConflictException.class, () -> authService.register("alice", "a@example.com", "password1"));

        when(userRepository.existsByUsernameIgnoreCase("bob")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("b@example.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> authService.register("bob", "B@Example.com", "password1"));

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_weakOrTooLongPassword_isBadRequest_beforeAnyLookup() {
        assertThrows(BadRequestException.class, () -> authService.register("alice", "a@example.com", "short"));
        assertThrows(BadRequestException.class, () -> authService.register("alice", "a@example.com", "x".repeat(73)));
        assertThrows(BadRequestException.class, () -> authService.register("alice", "   ", "password1"));
        verify(userRepository, never()).existsByUsernameIgnoreCase(any());
    }

    @Test
    void register_concurrentDuplicate_surfacesAs409NotAs500() {
        customerRoleExists();
        when(passwordHasher.hash("password1")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThrows(ConflictException.class, () -> authService.register("alice", "a@example.com", "password1"));
        verify(cartService, never()).createCartForUser(any());
    }

    private static User alice() {
        User u = new User();
        u.setId(11L);
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setPassword("bcrypt-hash");
        u.setActive(true);
        u.setRoles(new HashSet<>(Set.of(new Role(1L, Role.CUSTOMER))));
        return u;
    }

    @Test
    void login_withEmail_looksUpByEmail_notByUsername() {
        User u = alice();
        when(userRepository.findByEmailIgnoreCase("Alice@Example.com")).thenReturn(Optional.of(u));
        when(passwordHasher.matches("secret", "bcrypt-hash")).thenReturn(true);
        when(refreshTokenService.issueNewFamily(u)).thenReturn("refresh-raw");

        AuthResponse response = authService.login("  Alice@Example.com ", "secret", "1.2.3.4");

        assertEquals("refresh-raw", response.refreshToken());
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void login_withUnknownEmail_isSameBadCredentialsAsWrongPassword() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash(any())).thenReturn("dummy");
        when(passwordHasher.matches(any(), any())).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.login("nobody@example.com", "whatever", "1.2.3.4"));
        assertEquals("Tên đăng nhập hoặc mật khẩu không chính xác.", ex.getMessage());
    }
}
