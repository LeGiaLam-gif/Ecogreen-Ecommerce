package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.MyNotificationResponse;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.notification.NotificationQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The caller's own notifications (B10). Any logged-in user may call it; the rights are ownership-based, so there is no
 * permission code. The user id is ALWAYS the one in the verified token: the endpoint has no user parameter, and a
 * {@code userId} in the query string is ignored.
 */
@RestController
@RequestMapping("/api/v1/users/me/notifications")
public class NotificationV1Controller {

    @Autowired private NotificationQueryService notificationQueryService;

    @GetMapping
    public ApiResponse<List<MyNotificationResponse>> mine(@RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size,
                                                          @RequestParam(required = false) String sort,
                                                          HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) {
            throw new UnauthorizedException("Bạn cần đăng nhập.");
        }
        return PageResponse.of(
                notificationQueryService.listMine(current.getUserId(), NotificationQueryService.myPageable(page, size, sort)),
                MyNotificationResponse::from);
    }
}
