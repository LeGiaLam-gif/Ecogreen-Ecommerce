package com.example.backend.service;

import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogFilter.class);
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final AuditLogService auditLogService;

    public AuditLogFilter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin/")
                || READ_METHODS.contains(request.getMethod().toUpperCase());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
            if (current != null) {
                try {
                    auditLogService.recordAdminMutation(current.getUserId(), request.getMethod(),
                            request.getRequestURI(), response.getStatus());
                } catch (RuntimeException e) {
                    log.error("Could not record admin request audit log for {}", request.getRequestURI(), e);
                }
            }
        }
    }
}
