package com.example.backend.security;

import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the "Authorization: Bearer <token>" header, resolves it to a real
 * {@link User} row via {@link AuthTokenStore}, and attaches a {@link CurrentUser}
 * to the request. Endpoints that require authentication/authorization check
 * request.getAttribute("currentUser") - the frontend's claimed userId/role is
 * never trusted for anything security-sensitive.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String REQUEST_ATTR = "currentUser";

    private final AuthTokenStore tokenStore;
    private final UserRepository userRepository;

    public AuthInterceptor(AuthTokenStore tokenStore, UserRepository userRepository) {
        this.tokenStore = tokenStore;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Long userId = tokenStore.resolveUserId(token);
            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.isActive()) {
                    boolean isAdmin = user.hasRole(Role.ADMIN);
                    request.setAttribute(REQUEST_ATTR, new CurrentUser(user.getId(), isAdmin));
                }
            }
        }
        return true; // resolution only; individual controllers enforce requirements
    }
}
