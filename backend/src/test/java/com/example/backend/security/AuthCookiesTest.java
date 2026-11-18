package com.example.backend.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookiesTest {

    @Test
    void secureSessionCookiesAreHttpOnlyLaxAndRemainSessionScopedWhenRememberIsOff() {
        AuthCookies cookies = new AuthCookies(true, 900, 14);
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookies.setSession(response, "access", "refresh", false);

        List<String> sessionCookies = response.getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("ecogreen_"))
                .toList();
        assertTrue(sessionCookies.stream().allMatch(value -> value.contains("HttpOnly")
                && value.contains("SameSite=Lax") && value.contains("Secure")));
        assertTrue(sessionCookies.stream().noneMatch(value -> value.contains("Max-Age")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        String csrf = cookies.csrfToken(request, response);
        String csrfCookie = response.getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst().orElseThrow();
        assertTrue(csrfCookie.contains(csrf));
        assertTrue(csrfCookie.contains("Secure"));
        assertFalse(csrfCookie.contains("HttpOnly"));
    }

    @Test
    void csrfUsesDoubleSubmitCookieAndHeader() {
        AuthCookies cookies = new AuthCookies(false, 900, 14);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setCookies(new Cookie(AuthCookies.CSRF, "a-secure-csrf-token"));

        assertFalse(cookies.isCsrfValid(request));
        request.addHeader(AuthCookies.CSRF_HEADER, "a-secure-csrf-token");
        assertTrue(cookies.isCsrfValid(request));
    }
}
