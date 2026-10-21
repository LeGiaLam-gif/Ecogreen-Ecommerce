package com.example.backend.dto;

import com.example.backend.entity.AnalyticsEvent;
import com.example.backend.service.AnalyticsPayloadJson;
import com.example.backend.service.ReportTime;

import java.time.Instant;
import java.util.Map;

/** One analytics event for the admin list. {@code createdAt} is a UTC instant; {@code payload} is a flat object. */
public record AnalyticsEventResponse(
        Long id,
        String eventType,
        Long userId,
        String sessionId,
        Map<String, Object> payload,
        Instant createdAt) {

    public static AnalyticsEventResponse from(AnalyticsEvent event) {
        return new AnalyticsEventResponse(event.getId(), event.getEventType(), event.getUserId(), event.getSessionId(),
                AnalyticsPayloadJson.read(event.getPayload()), ReportTime.toUtc(event.getCreatedAt()));
    }
}
