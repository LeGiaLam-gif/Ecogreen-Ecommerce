package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.AuditLogResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.Permissions;
import com.example.backend.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogV1Controller {

    @Autowired private AuthGuard authGuard;
    @Autowired private AuditLogService auditLogService;

    @GetMapping
    public ApiResponse<List<AuditLogResponse>> list(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.AUDIT_VIEW);
        return PageResponse.of(auditLogService.list(AuditLogService.pageable(page, size)), AuditLogResponse::from);
    }
}
