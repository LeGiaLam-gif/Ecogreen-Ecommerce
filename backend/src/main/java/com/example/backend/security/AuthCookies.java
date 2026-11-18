package com.example.backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Component
public class AuthCookies {

    public static final String ACCESS = "ecogreen_access";
    public static final String REFRESH = "ecogreen_refresh";
    public static final String CSRF = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean secure;
    private final long accessTtlSeconds;
    private final long refreshTtlDays;

    public AuthCookies(@Value("${ecogreen.auth.cookie-secure:false}") boolean secure,
                       @Value("${ecogreen.jwt.access-ttl-seconds:900}") long accessTtlSeconds,
                       @Value("${ecogreen.jwt.refresh-ttl-days:14}") long refreshTtlDays) {
        this.secure = secure;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlDays = refreshTtlDays;
    }

    public void setSession(HttpServletResponse response, String accessToken, String refreshToken, boolean remember) {
        add(response, ACCESS, accessToken, "/api", accessTtlSeconds, remember);
        add(response, REFRESH, refreshToken, "/api/v1/auth", refreshTtlDays * 24 * 60 * 60, remember);
    }

    public void clearSession(HttpServletResponse response) {
        add(response, ACCESS, "", "/api", 0L, true);
        add(response, REFRESH, "", "/api/v1/auth", 0L, true);
    }

    public String getRefreshToken(HttpServletRequest request) {
        return cookieValue(request, REFRESH);
    }

    public String csrfToken(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieValue(request, CSRF);
        if (token == null || !token.matches("[A-Za-z0-9_-]{40,64}")) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            add(response, CSRF, token, "/api", null, false);
        }
        return token;
    }

    public boolean hasSessionCookie(HttpServletRequest request) {
        return cookieValue(request, ACCESS) != null || cookieValue(request, REFRESH) != null;
    }

    public boolean isCsrfValid(HttpServletRequest request) {
        String cookie = cookieValue(request, CSRF);
        String header = request.getHeader(CSRF_HEADER);
        return cookie != null && header != null
                && MessageDigest.isEqual(cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void add(HttpServletResponse response, String name, String value, String path, Long maxAgeSeconds,
                     boolean remember) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(!CSRF.equals(name))
                .secure(secure)
                .sameSite("Lax")
                .path(path);
        if (maxAgeSeconds != null && (remember || maxAgeSeconds == 0L)) {
            builder.maxAge(Duration.ofSeconds(maxAgeSeconds));
        }
        response.addHeader("Set-Cookie", builder.build().toString());
    }
}
