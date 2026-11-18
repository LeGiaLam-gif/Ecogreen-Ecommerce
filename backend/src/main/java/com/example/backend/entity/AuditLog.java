package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 60)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(Long actorUserId, String action, String resourceType, String resourceId,
                    String httpMethod, String requestPath, Integer responseStatus, String detailsJson) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.responseStatus = responseStatus;
        this.detailsJson = detailsJson;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestPath() { return requestPath; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getDetailsJson() { return detailsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
