package com.example.backend.controller;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.TokenPairResponse;
import com.example.backend.dto.UserResponse;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.exception.GoogleLoginUnavailableException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.JwtService;
import com.example.backend.security.LoginThrottle;
import com.example.backend.security.TestJwt;
import com.example.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B01-P2: the six /api/v1/auth endpoints through standalone MockMvc (real handler, interceptor, guard, JwtService). */
class AuthControllerTest {

    private AuthService authService;
    private UserRepository userRepository;
    private JwtService jwt;
    private MockMvc mvc;

    private static User user() {
        User u = new User();
        u.setId(3L);
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setRoles(new HashSet<>(Set.of(new Role(1L, Role.CUSTOMER))));
        return u;
    }

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        userRepository = mock(UserRepository.class);
        jwt = new JwtService(TestJwt.SECRET, 900);

        AuthGuard guard = new AuthGuard();
        ReflectionTestUtils.setField(guard, "userRepository", userRepository);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "authGuard", guard);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwt, userRepository))
                .build();
    }

    @Test
    void controller_isMountedOnlyUnderApiV1Auth() {
        assertArrayEquals(new String[]{"/api/v1/auth"}, AuthController.class.getAnnotation(RequestMapping.class).value());
    }

    @Test
    void login_returnsEnvelopeWithTokensAndUser() throws Exception {
        UserResponse ur = UserResponse.from(user());
        when(authService.login(eq("alice"), eq("pw"), anyString()))
                .thenReturn(new AuthResponse("acc", "ref", 900, ur));

        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("acc"))
                .andExpect(jsonPath("$.data.refreshToken").value("ref"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.username").value("alice"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.user.permissions").isArray())
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("\"meta\":null"));
        assertTrue(!result.getResponse().getContentAsString().contains("password"));
    }

    @Test
    void login_missingPassword_isValidationError_andBadCredentialsIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.password").exists());

        when(authService.login(anyString(), anyString(), anyString()))
                .thenThrow(new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác."));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void login_throttled_returns429RateLimitedWithRetryAfter() throws Exception {
        when(authService.login(anyString(), anyString(), anyString()))
                .thenThrow(new LoginThrottle.TooManyAttemptsException(120));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"bad\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void register_returns201WithUserEnvelope() throws Exception {
        when(authService.register("alice", "alice@example.com", "pw")).thenReturn(user());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"pw\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void google_returnsSameShapeAsLogin_andUnconfiguredIs503() throws Exception {
        when(authService.loginWithGoogle(any())).thenReturn(new AuthResponse("acc", "ref", 900, UserResponse.from(user())));
        mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"x.y.z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("acc"));

        when(authService.loginWithGoogle(any())).thenThrow(new GoogleLoginUnavailableException("not configured"));
        mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"x.y.z\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void refresh_returnsTokenPairEnvelope_andInvalidTokenIs401() throws Exception {
        when(authService.refresh("good")).thenReturn(new TokenPairResponse("acc2", "ref2", 900));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("acc2"))
                .andExpect(jsonPath("$.data.refreshToken").value("ref2"))
                .andExpect(jsonPath("$.data.user").doesNotExist());

        when(authService.refresh("revoked")).thenThrow(new UnauthorizedException("Phiên đăng nhập không hợp lệ."));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"revoked\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logout_revokesToken_andReturnsNullData() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"raw\"}"))
                .andExpect(status().isOk())
                .andReturn();

        verify(authService).logout("raw");
        assertTrue(result.getResponse().getContentAsString().contains("\"data\":null"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void me_withValidAccessToken_returnsCurrentUser_andDeactivatedUserIs401() throws Exception {
        User u = user();
        String token = jwt.issueAccessToken(u);
        when(userRepository.existsByIdAndActiveTrue(3L)).thenReturn(true);
        when(userRepository.findById(3L)).thenReturn(Optional.of(u));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.username").value("alice"));

        when(userRepository.existsByIdAndActiveTrue(3L)).thenReturn(false); // account deactivated meanwhile
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
