package com.example.backend.service.notification;

import com.example.backend.entity.NotificationType;

/**
 * What an {@link EmailSender} gets: the plain-text and the HTML version of one email (sent as multipart/alternative).
 * {@code type} and {@code userId} exist so a sender that does not deliver (the logging one) can say WHAT it skipped without
 * ever printing {@code to}, {@code subject} or a body.
 */
public record EmailMessage(NotificationType type, Long userId, String to, String subject, String textBody, String htmlBody) {

    /** The address and the content are personal data: keep them out of logs, even when a record is printed by mistake. */
    @Override
    public String toString() {
        return "EmailMessage[userId=" + userId + ", type=" + type + "]";
    }
}
