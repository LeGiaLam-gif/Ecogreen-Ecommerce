package com.example.backend.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/** Anonymous or authenticated storefront analytics event. Payload values are sanitized by AnalyticsEventService. */
public record AnalyticsTrackRequest(
        @Size(max = 50) String eventType,
        @Size(max = 100) String sessionId,
        Map<String, Object> payload) {
}
