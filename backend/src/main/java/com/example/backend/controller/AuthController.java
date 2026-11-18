package com.example.backend.controller;

import com.example.backend.api.ApiError;
import com.example.backend.api.ApiErrorCode;
import com.example.backend.api.ApiResponse;
import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.FacebookAuthRequest;
import com.example.backend.dto.ForgotPasswordRequest;
import com.example.backend.dto.GoogleAuthRequest;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.dto.ResetPasswordRequest;
import com.example.backend.dto.TokenPairResponse;
import com.example.backend.dto.UserResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.FacebookLoginUnavailableException;
import com.example.backend.exception.GoogleLoginUnavailableException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthCookies;
import com.example.backend.security.LoginThrottle;
import com.example.backend.security.PasswordResetThrottle;
import com.example.backend.service.AuthService;
import com.example.backend.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private AuthCookies authCookies;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest body) {
        User user = authService.register(body.username(), body.email(), body.password());
        return ResponseEntity.status(201).body(ApiResponse.of(UserResponse.from(user)));
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                           HttpServletResponse response,
                                           @RequestHeader(value = "X-Remember-Me", defaultValue = "true") boolean remember) {
        // getRemoteAddr(): X-Forwarded-For is client-controlled and deliberately not trusted for throttling.
        AuthResponse session = authService.login(body.username(), body.password(), request.getRemoteAddr());
        authCookies.setSession(response, session.accessToken(), session.refreshToken(), remember);
        authCookies.csrfToken(request, response);
        return ApiResponse.of(session.user());
    }

    @PostMapping("/google")
    public ApiResponse<UserResponse> loginWithGoogle(@RequestBody GoogleAuthRequest request, HttpServletRequest servletRequest,
                                                     HttpServletResponse response,
                                                     @RequestHeader(value = "X-Remember-Me", defaultValue = "true") boolean remember) {
        AuthResponse session = authService.loginWithGoogle(request);
        authCookies.setSession(response, session.accessToken(), session.refreshToken(), remember);
        authCookies.csrfToken(servletRequest, response);
        return ApiResponse.of(session.user());
    }

    @PostMapping("/facebook")
    public ApiResponse<UserResponse> loginWithFacebook(@Valid @RequestBody FacebookAuthRequest body,
                                                       HttpServletRequest request, HttpServletResponse response,
                                                       @RequestHeader(value = "X-Remember-Me", defaultValue = "true") boolean remember) {
        AuthResponse session = authService.loginWithFacebook(body);
        authCookies.setSession(response, session.accessToken(), session.refreshToken(), remember);
        authCookies.csrfToken(request, response);
        return ApiResponse.of(session.user());
    }

    /** Always 200 {data:null} for a well-formed request: the answer never reveals whether the e-mail has an account. */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body, HttpServletRequest request) {
        passwordResetService.requestReset(body.email(), request.getRemoteAddr());
        return ApiResponse.of(null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        passwordResetService.resetPassword(body.token(), body.password());
        return ApiResponse.of(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refresh(HttpServletRequest request, HttpServletResponse response,
                                     @RequestHeader(value = "X-Remember-Me", defaultValue = "true") boolean remember) {
        TokenPairResponse session = authService.refresh(authCookies.getRefreshToken(request));
        authCookies.setSession(response, session.accessToken(), session.refreshToken(), remember);
        authCookies.csrfToken(request, response);
        return ApiResponse.of(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(authCookies.getRefreshToken(request));
        authCookies.clearSession(response);
        return ApiResponse.of(null);
    }

    @GetMapping("/csrf")
    public ApiResponse<String> csrf(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.of(authCookies.csrfToken(request, response));
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

    @ExceptionHandler(FacebookLoginUnavailableException.class)
    public ResponseEntity<ApiError> handleFacebookUnavailable(FacebookLoginUnavailableException ex) {
        return ResponseEntity.status(503).body(ApiError.of(ApiErrorCode.INTERNAL_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(PasswordResetThrottle.TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyResetRequests(PasswordResetThrottle.TooManyRequestsException ex) {
        return ResponseEntity.status(ApiErrorCode.RATE_LIMITED.httpStatus())
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiError.of(ApiErrorCode.RATE_LIMITED, ex.getMessage()));
    }

    @ExceptionHandler(LoginThrottle.TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(LoginThrottle.TooManyAttemptsException ex) {
        return ResponseEntity.status(ApiErrorCode.RATE_LIMITED.httpStatus())
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiError.of(ApiErrorCode.RATE_LIMITED, ex.getMessage()));
    }
}
