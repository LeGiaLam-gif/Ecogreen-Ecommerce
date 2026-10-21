package com.example.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One business event recorded for analytics (B09). Append-only: rows are inserted by {@code AnalyticsEventService} and never
 * updated. B09 owns this table; it never writes to any other module's table.
 *
 * <p>{@code payload} is a flat JSON object stored as PostgreSQL {@code jsonb}; it is kept as text in Java so no JSON library
 * is needed (see {@code AnalyticsPayloadJson}). {@code userId} is a plain id (no relation): the foreign key
 * {@code fk_analytics_events_user ... ON DELETE SET NULL} lives in V80.
 */
@Entity
@Table(name = "analytics_events",
        indexes = @Index(name = "idx_analytics_events_type_created", columnList = "event_type, created_at"))
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    /** Local time of the JVM / database session zone (decision D-9), like every other timestamp column. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AnalyticsEvent() {
    }

    public AnalyticsEvent(String eventType, Long userId, String sessionId, String payload) {
        this.eventType = eventType;
        this.userId = userId;
        this.sessionId = sessionId;
        this.payload = payload;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public String getEventType() { return eventType; }

    public Long getUserId() { return userId; }

    public String getSessionId() { return sessionId; }

    public String getPayload() { return payload; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
