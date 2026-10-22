package com.example.backend.dto;

import com.example.backend.entity.Notification;
import com.example.backend.service.ReportTime;

import java.time.Instant;

/**
 * A notification as its owner sees it. {@code body} is plain text. There is no failure reason and no dedupe key: those are
 * internal. Times are UTC instants.
 */
public record MyNotificationResponse(Long id, String type, String channel, String title, String body, String status,
                                     Instant sentAt, Instant createdAt) {

    public static MyNotificationResponse from(Notification n) {
        return new MyNotificationResponse(n.getId(), n.getType().name(), n.getChannel().name(), n.getTitle(), n.getBody(),
                n.getStatus().name(), ReportTime.toUtc(n.getSentAt()), ReportTime.toUtc(n.getCreatedAt()));
    }
}
