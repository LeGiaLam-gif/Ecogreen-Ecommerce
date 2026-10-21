package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import com.example.backend.dto.AnalyticsEventResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.Permissions;
import com.example.backend.service.AnalyticsEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin read access to the recorded analytics events (B09). Read only: nothing here writes an event. */
@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsV1Controller {

    @Autowired private AnalyticsEventService analyticsEventService;
    @Autowired private AuthGuard authGuard;

    /**
     * Newest first by default. {@code type} must be a known event type (400 otherwise); {@code from} / {@code to} are
     * ISO-8601 instants with an offset, window {@code [from, to)}, default the last 30 days, at most 366 days.
     */
    @GetMapping("/events")
    public ApiResponse<List<AnalyticsEventResponse>> events(@RequestParam(required = false) String type,
                                                            @RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @RequestParam(required = false) String sort,
                                                            HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.ANALYTICS_VIEW);
        return PageResponse.of(
                analyticsEventService.listEvents(type, from, to, AnalyticsEventService.pageable(page, size, sort)),
                AnalyticsEventResponse::from);
    }
}
