package com.example.backend.dto;

import com.example.backend.entity.Notification;
import com.example.backend.service.ReportTime;

import java.time.Instant;

/**
 * One row of the delivery log for staff debugging. It carries the SHORT failure code (e.g. SEND_ERROR), never an exception
 * message, and no body and no email address, so {@code analytics:view} does not expose customer content.
 */
public record AdminNotificationResponse(Long id, Long userId, String type, String channel, String title, String status,
                                        String failureReason, Instant sentAt, Instant createdAt) {

    public static AdminNotificationResponse from(Notification n) {
        return new AdminNotificationResponse(n.getId(), n.getUserId(), n.getType().name(), n.getChannel().name(), n.getTitle(),
                n.getStatus().name(), n.getFailureReason(), ReportTime.toUtc(n.getSentAt()), ReportTime.toUtc(n.getCreatedAt()));
    }
}
