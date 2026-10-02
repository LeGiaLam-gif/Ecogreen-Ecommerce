package com.example.backend.dto;

import com.example.backend.entity.ReturnRequest;

public class ReturnResponse {
    public Long id;
    public Long orderId;
    public Long userId;
    public String username;
    public String reason;
    public String description;
    public String imageUrl;
    public String status;
    public String adminNote;
    public String createdAt;

    public static ReturnResponse from(ReturnRequest rr) {
        ReturnResponse r = new ReturnResponse();
        r.id = rr.getId();
        r.orderId = rr.getOrder() != null ? rr.getOrder().getId() : null;
        r.userId = rr.getUser() != null ? rr.getUser().getId() : null;
        r.username = rr.getUser() != null ? rr.getUser().getUsername() : null;
        r.reason = rr.getReason();
        r.description = rr.getDescription() != null ? rr.getDescription() : "";
        r.imageUrl = rr.getImageUrl() != null ? rr.getImageUrl() : "";
        r.status = rr.getStatus() != null ? rr.getStatus().name() : "";
        r.adminNote = rr.getAdminNote() != null ? rr.getAdminNote() : "";
        r.createdAt = rr.getCreatedAt() != null ? rr.getCreatedAt().toString() : "";
        return r;
    }
}
