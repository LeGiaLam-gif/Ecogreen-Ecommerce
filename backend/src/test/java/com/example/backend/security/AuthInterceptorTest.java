package com.example.backend.security;

import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private final JwtService jwt = new JwtService(TestJwt.SECRET, 900);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthCookies cookies = new AuthCookies(false, 900, 14);
    private final AuthInterceptor interceptor = new AuthInterceptor(jwt, users, cookies);

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setRoles(new HashSet<>(Set.of(new Role(1L, Role.ADMIN))));
        return u;
    }

    private CurrentUser run(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        if (authorization != null) request.addHeader("Authorization", authorization);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        return (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
    }

    @Test
    void validToken_activeUser_attachesCurrentUserFromClaims() {
        when(users.existsByIdAndActiveTrue(9L)).thenReturn(true);

        CurrentUser current = run("Bearer " + jwt.issueAccessToken(user(9)));

        assertNotNull(current);
        assertEquals(9L, current.getUserId());
        assertTrue(current.isAdmin());
    }

    @Test
    void interceptor_deactivatedUser_isRejected() {
        when(users.existsByIdAndActiveTrue(9L)).thenReturn(false); // deactivated or deleted

        assertNull(run("Bearer " + jwt.issueAccessToken(user(9))));
    }

    @Test
    void expiredTamperedLegacyOrMissingToken_leavesNoCurrentUser_andSkipsDbLookup() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String expired = new JwtService(TestJwt.SECRET, 900, past).issueAccessToken(user(9));
        String valid = jwt.issueAccessToken(user(9));

        assertNull(run("Bearer " + expired));
        assertNull(run("Bearer " + valid.substring(0, valid.length() - 3) + "abc"));
        assertNull(run("Bearer 3f2b8c1e-5a4d-4e0b-9a52-0c3d6f7e8a91")); // legacy UUID session
        assertNull(run("Basic abc"));
        assertNull(run(null));
        verify(users, never()).existsByIdAndActiveTrue(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void cookieTokenAuthenticatesAndStateChangingCookieRequestsRequireCsrf() {
        when(users.existsByIdAndActiveTrue(9L)).thenReturn(true);
        MockHttpServletRequest authenticated = new MockHttpServletRequest("GET", "/api/orders");
        authenticated.setCookies(new Cookie(AuthCookies.ACCESS, jwt.issueAccessToken(user(9))));
        assertTrue(interceptor.preHandle(authenticated, new MockHttpServletResponse(), new Object()));
        assertNotNull(authenticated.getAttribute(AuthInterceptor.REQUEST_ATTR));

        MockHttpServletRequest unsafe = new MockHttpServletRequest("POST", "/api/orders");
        unsafe.setCookies(new Cookie(AuthCookies.REFRESH, "opaque"));
        org.junit.jupiter.api.Assertions.assertThrows(ForbiddenException.class,
                () -> interceptor.preHandle(unsafe, new MockHttpServletResponse(), new Object()));

        unsafe.setCookies(new Cookie(AuthCookies.REFRESH, "opaque"), new Cookie(AuthCookies.CSRF, "csrf"));
        unsafe.addHeader(AuthCookies.CSRF_HEADER, "csrf");
        assertTrue(interceptor.preHandle(unsafe, new MockHttpServletResponse(), new Object()));
    }
}
