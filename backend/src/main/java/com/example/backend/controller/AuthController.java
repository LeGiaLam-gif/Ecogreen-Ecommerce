package com.example.backend.controller;

import com.example.backend.api.ApiError;
import com.example.backend.api.ApiErrorCode;
import com.example.backend.api.ApiResponse;
import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.GoogleAuthRequest;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.RefreshTokenRequest;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.dto.TokenPairResponse;
import com.example.backend.dto.UserResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.GoogleLoginUnavailableException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.LoginThrottle;
import com.example.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** B01-P2: all authentication traffic lives under /api/v1/auth (contract: docs/ai/contracts/B01-auth.md). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private AuthGuard authGuard;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@RequestBody RegisterRequest body) {
        User user = authService.register(body.username(), body.email(), body.password());
        return ResponseEntity.status(201).body(ApiResponse.of(UserResponse.from(user)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        // getRemoteAddr(): X-Forwarded-For is client-controlled and deliberately not trusted for throttling.
        return ApiResponse.of(authService.login(body.username(), body.password(), request.getRemoteAddr()));
    }

    @PostMapping("/google")
    public ApiResponse<AuthResponse> loginWithGoogle(@RequestBody GoogleAuthRequest request) {
        return ApiResponse.of(authService.loginWithGoogle(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest body) {
        return ApiResponse.of(authService.refresh(body.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest body) {
        authService.logout(body.refreshToken());
        return ApiResponse.of(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(HttpServletRequest request) {
        return ApiResponse.of(UserResponse.from(authGuard.requireUser(request)));
    }

    // Local handlers (checked before GlobalExceptionHandler). Both use the /api/v1 error contract.

    /** B01-P1: fail closed when Google login is not configured / Google keys are unreachable. */
    @ExceptionHandler(GoogleLoginUnavailableException.class)
    public ResponseEntity<ApiError> handleGoogleUnavailable(GoogleLoginUnavailableException ex) {
        return ResponseEntity.status(503).body(ApiError.of(ApiErrorCode.INTERNAL_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(LoginThrottle.TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(LoginThrottle.TooManyAttemptsException ex) {
        return ResponseEntity.status(ApiErrorCode.RATE_LIMITED.httpStatus())
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiError.of(ApiErrorCode.RATE_LIMITED, ex.getMessage()));
    }
}
