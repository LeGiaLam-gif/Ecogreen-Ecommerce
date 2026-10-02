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

    @Autowired private UserRepository userRepository;

    public User requireUser(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        return userRepository.findById(current.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Vui lòng đăng nhập để tiếp tục."));
    }

    public boolean isAdmin(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        return current != null && current.isAdmin();
    }

    public void requireAdmin(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        if (!current.isAdmin()) throw new ForbiddenException("Yêu cầu quyền Quản trị viên (Admin).");
    }
}
