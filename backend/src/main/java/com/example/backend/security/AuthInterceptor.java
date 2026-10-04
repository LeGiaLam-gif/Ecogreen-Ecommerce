package com.example.backend.security;

import com.example.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads "Authorization: Bearer <jwt>", verifies signature + expiry, and attaches a {@link CurrentUser} to the request.
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

    public AuthInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                CurrentUser current = jwtService.parse(token);
                if (userRepository.existsByIdAndActiveTrue(current.getUserId())) {
                    request.setAttribute(REQUEST_ATTR, current);
                }
            } catch (RuntimeException e) {
                // Invalid, tampered, unsigned, expired or legacy (non-JWT) token: treated as unauthenticated.
                // The token itself is never logged.
                log.debug("Rejected bearer token: {}", e.getClass().getSimpleName());
            }
        }
        return true; // resolution only; individual controllers enforce requirements
    }
}
