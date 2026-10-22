package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.AdminNotificationResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.Permissions;
import com.example.backend.service.notification.NotificationQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Delivery log for staff (B10), read only. Needs {@code analytics:view}; an unknown {@code status} is a 400. */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationV1Controller {

    @Autowired private NotificationQueryService notificationQueryService;
    @Autowired private AuthGuard authGuard;

    @GetMapping
    public ApiResponse<List<AdminNotificationResponse>> list(@RequestParam(required = false) String status,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size,
                                                             @RequestParam(required = false) String sort,
                                                             HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.ANALYTICS_VIEW);
        return PageResponse.of(
                notificationQueryService.listAll(status, NotificationQueryService.adminPageable(page, size, sort)),
                AdminNotificationResponse::from);
    }
}
