package com.example.backend.entity;

/**
 * PENDING: row written, not yet handed to the sender. SENT: the SMTP server accepted the message. FAILED: not sent; the reason
 * code is in {@code notifications.failure_reason}. In development (no SMTP host) a notification is FAILED with reason
 * EMAIL_NOT_CONFIGURED, because nothing was delivered.
 */
public enum NotificationStatus {
    PENDING, SENT, FAILED
}
