package com.example.backend.controller;

import com.example.backend.dto.AnalyticsTrackRequest;
import com.example.backend.exception.BadRequestException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.AnalyticsEventService;
import com.example.backend.service.AnalyticsEventTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public write-only endpoint for the limited set of storefront analytics events. */
@RestController
@RequestMapping("/api/v1/analytics/events")
public class AnalyticsTrackV1Controller {

    @Autowired private AnalyticsEventService analyticsEventService;

    @PostMapping
    public ResponseEntity<Void> track(@Valid @RequestBody AnalyticsTrackRequest body, HttpServletRequest request) {
        if (body.eventType() == null || !AnalyticsEventTypes.CLIENT_TRACKABLE.contains(body.eventType())) {
            throw new BadRequestException("Loại sự kiện analytics không hợp lệ.");
        }
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        analyticsEventService.track(body.eventType(), current == null ? null : current.getUserId(),
                body.sessionId(), body.payload());
        return ResponseEntity.noContent().build();
    }
}
