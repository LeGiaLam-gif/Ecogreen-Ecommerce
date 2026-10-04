package com.example.backend.security;

import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation") // requireAdmin/isAdmin are deprecated by B01-P3 but must keep working unchanged
class AuthGuardPermissionTest {

    private final AuthGuard guard = new AuthGuard();

    private static MockHttpServletRequest requestOf(List<String> roles, List<String> permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthInterceptor.REQUEST_ATTR, new CurrentUser(1L, roles, permissions));
        return request;
    }

    @Test
    void requirePermission_withoutPermission_throwsForbidden() {
        MockHttpServletRequest request = requestOf(List.of("MANAGER"), List.of(Permissions.PRODUCT_VIEW));
        assertThrows(ForbiddenException.class, () -> guard.requirePermission(request, Permissions.PRODUCT_CREATE));
    }

    @Test
    void requirePermission_withPermission_passes() {
        MockHttpServletRequest request = requestOf(List.of("MANAGER"), List.of(Permissions.PRODUCT_CREATE));
        assertDoesNotThrow(() -> guard.requirePermission(request, Permissions.PRODUCT_CREATE));
    }

    @Test
    void requirePermission_unauthenticated_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class,
                () -> guard.requirePermission(new MockHttpServletRequest(), Permissions.PRODUCT_VIEW));
    }

    @Test
    void requirePermission_roleAloneGrantsNothing_onlyTheTokenPermissionsCount() {
        // Even an ADMIN role with an empty permission claim is denied by the permission guard (permissions come from the DB).
        MockHttpServletRequest request = requestOf(List.of("ADMIN"), List.of());
        assertThrows(ForbiddenException.class, () -> guard.requirePermission(request, Permissions.PRODUCT_VIEW));
    }

    @Test
    void requireAnyPermission_oneOfMany_passes() {
        MockHttpServletRequest request = requestOf(List.of("MANAGER"), List.of(Permissions.ORDER_VIEW));
        assertDoesNotThrow(() -> guard.requireAnyPermission(request, Permissions.PRODUCT_VIEW, Permissions.ORDER_VIEW));
    }

    @Test
    void requireAnyPermission_noneOfMany_throwsForbidden_andNoCodes_throwsForbidden() {
        MockHttpServletRequest request = requestOf(List.of("MANAGER"), List.of(Permissions.ORDER_VIEW));
        assertThrows(ForbiddenException.class,
                () -> guard.requireAnyPermission(request, Permissions.PRODUCT_VIEW, Permissions.PRODUCT_CREATE));
        assertThrows(ForbiddenException.class, () -> guard.requireAnyPermission(request));
        assertThrows(UnauthorizedException.class,
                () -> guard.requireAnyPermission(new MockHttpServletRequest(), Permissions.ORDER_VIEW));
    }

    @Test
    void requireAdmin_behaviourUnchanged_adminOnly() {
        assertDoesNotThrow(() -> guard.requireAdmin(requestOf(List.of("ADMIN"), List.of())));
        assertTrue(guard.isAdmin(requestOf(List.of("ADMIN"), List.of())));

        // A MANAGER - even holding permissions - is still denied by the legacy guard (documented consequence).
        MockHttpServletRequest manager = requestOf(List.of("MANAGER"), Permissions.forRole("MANAGER"));
        assertThrows(ForbiddenException.class, () -> guard.requireAdmin(manager));
        assertFalse(guard.isAdmin(manager));

        assertThrows(UnauthorizedException.class, () -> guard.requireAdmin(new MockHttpServletRequest()));
        assertFalse(guard.isAdmin(new MockHttpServletRequest()));
    }

    @Test
    void currentUser_keepsExistingAccessors_andAddsPermissionHelpers() {
        CurrentUser current = new CurrentUser(5L, List.of("ADMIN"), List.of(Permissions.AUDIT_VIEW));
        assertTrue(current.isAdmin());
        assertTrue(current.getRoles().contains("ADMIN"));
        assertTrue(current.getPermissions().contains(Permissions.AUDIT_VIEW));
        assertTrue(current.hasPermission(Permissions.AUDIT_VIEW));
        assertFalse(current.hasPermission(Permissions.USER_DISABLE));
        assertFalse(current.hasPermission(null));
        assertTrue(current.hasAnyPermission(Permissions.USER_DISABLE, Permissions.AUDIT_VIEW));
        assertFalse(current.hasAnyPermission());
    }
}
