package com.example.backend.security;

import com.example.backend.entity.User;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/** Small helper controllers use to enforce authentication/authorization server-side. */
@Component
public class AuthGuard {

    private static final String NO_PERMISSION = "Bạn không có quyền thực hiện thao tác này.";

    @Autowired private UserRepository userRepository;

    public User requireUser(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        return userRepository.findById(current.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Vui lòng đăng nhập để tiếp tục."));
    }

    /** @deprecated B01-P3: use {@link #requirePermission}. Unchanged ADMIN-only semantics; removal condition: all controllers migrated (end of B12). */
    @Deprecated
    public boolean isAdmin(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        return current != null && current.isAdmin();
    }

    /**
     * @deprecated B01-P3: use {@link #requirePermission}. Unchanged ADMIN-only semantics, so a MANAGER is denied here until
     * the owning module migrates its endpoint. Removal condition: all controllers migrated (end of B12).
     */
    @Deprecated
    public void requireAdmin(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        if (!current.isAdmin()) throw new ForbiddenException("Yêu cầu quyền Quản trị viên (Admin).");
    }

    /** 401 when unauthenticated, 403 when the token does not carry {@code code}. Use constants from {@link Permissions}. */
    public void requirePermission(HttpServletRequest request, String code) {
        CurrentUser current = requireCurrent(request);
        if (!current.hasPermission(code)) throw new ForbiddenException(NO_PERMISSION);
    }

    /** 401 when unauthenticated, 403 unless the token carries at least one of {@code codes}. */
    public void requireAnyPermission(HttpServletRequest request, String... codes) {
        CurrentUser current = requireCurrent(request);
        if (!current.hasAnyPermission(codes)) throw new ForbiddenException(NO_PERMISSION);
    }

    private CurrentUser requireCurrent(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        return current;
    }
}
