package com.example.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One notification sent (or attempted) to a user (B10). B10 owns this table. {@code body} is the PLAIN-TEXT version of the
 * message (what the customer sees in the app); the HTML sent by email is rendered again from the same template and is not
 * stored. {@code userId} is a plain id: the foreign key {@code fk_notifications_user ... ON DELETE CASCADE} lives in V90.
 */
@Entity
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_notifications_status_created", columnList = "status, created_at")
        },
        uniqueConstraints = @UniqueConstraint(name = "uq_notifications_dedupe", columnNames = {"user_id", "type", "dedupe_key"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel = NotificationChannel.EMAIL;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    /** Short reason CODE when FAILED (never an exception message); null otherwise. */
    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    /** Identifies the business event (e.g. {@code order:123:PAID}); with user and type it makes a second send impossible. */
    @Column(name = "dedupe_key", length = 100)
    private String dedupeKey;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(Long userId, NotificationType type, NotificationChannel channel, String title, String body,
                        NotificationStatus status, String failureReason, String dedupeKey) {
        this.userId = userId;
        this.type = type;
        this.channel = channel;
        this.title = title;
        this.body = body;
        this.status = status;
        this.failureReason = failureReason;
        this.dedupeKey = dedupeKey;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }

    public NotificationType getType() { return type; }

    public NotificationChannel getChannel() { return channel; }

    public String getTitle() { return title; }

    public String getBody() { return body; }

    public NotificationStatus getStatus() { return status; }

    public String getFailureReason() { return failureReason; }

    public String getDedupeKey() { return dedupeKey; }

    public LocalDateTime getSentAt() { return sentAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
