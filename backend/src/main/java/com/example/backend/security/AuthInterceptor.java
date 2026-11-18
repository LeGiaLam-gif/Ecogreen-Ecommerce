package com.example.backend.security;

import com.example.backend.repository.UserRepository;
import com.example.backend.exception.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves a bearer header or HttpOnly access cookie, verifies signature + expiry, and attaches a {@link CurrentUser}.
 * Deactivated or deleted users are rejected with one cheap indexed lookup (exists by id and is_active) - the full
 * User entity (with its EAGER roles) is NOT loaded; roles come from the token claims.
 * Resolution only: an invalid/absent token simply leaves no CurrentUser and the endpoint (AuthGuard) answers 401.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String REQUEST_ATTR = "currentUser";

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthCookies authCookies;

    public AuthInterceptor(JwtService jwtService, UserRepository userRepository, AuthCookies authCookies) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authCookies = authCookies;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isStateChanging(request.getMethod()) && authCookies.hasSessionCookie(request)
                && !authCookies.isCsrfValid(request)) {
            throw new ForbiddenException("Yêu cầu không hợp lệ. Vui lòng tải lại trang và thử lại.");
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ")
                ? header.substring(7).trim()
                : cookieValue(request, AuthCookies.ACCESS);
        if (token != null && !token.isBlank()) {
            try {
                CurrentUser current = jwtService.parse(token);
                if (userRepository.existsByIdAndActiveTrue(current.getUserId())) {
                    request.setAttribute(REQUEST_ATTR, current);
                }
            } catch (RuntimeException e) {
                // Invalid, tampered, unsigned, expired or legacy (non-JWT) token: treated as unauthenticated.
                // The token itself is never logged.
                log.debug("Rejected access token: {}", e.getClass().getSimpleName());
            }
        }
        return true; // resolution only; individual controllers enforce requirements
    }

    private boolean isStateChanging(String method) {
        return !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method) || "TRACE".equalsIgnoreCase(method));
    }

    private String cookieValue(HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
