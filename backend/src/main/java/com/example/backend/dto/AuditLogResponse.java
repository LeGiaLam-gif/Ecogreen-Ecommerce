package com.example.backend.dto;

import com.example.backend.entity.AuditLog;
import com.example.backend.service.ReportTime;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String action,
        String resourceType,
        String resourceId,
        String httpMethod,
        String requestPath,
        Integer responseStatus,
        String detailsJson,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getAction(), log.getResourceType(),
                log.getResourceId(), log.getHttpMethod(), log.getRequestPath(), log.getResponseStatus(),
                log.getDetailsJson(), ReportTime.toUtc(log.getCreatedAt()));
    }
}
