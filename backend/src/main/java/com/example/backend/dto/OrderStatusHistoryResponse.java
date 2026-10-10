package com.example.backend.dto;

import com.example.backend.entity.OrderStatusHistory;
import java.time.LocalDateTime;

/**
 * One timeline entry. {@code changedBy} and {@code note} are internal (staff user ids, refund markers), so they are only
 * filled for a staff caller ({@code order:view}); the order's owner sees the statuses and the times.
 */
public record OrderStatusHistoryResponse(Long id, String oldStatus, String newStatus, Long changedBy, String note,
                                         LocalDateTime changedAt) {

    public static OrderStatusHistoryResponse from(OrderStatusHistory h, boolean includeInternal) {
        return new OrderStatusHistoryResponse(
                h.getId(),
                h.getOldStatus() == null ? null : h.getOldStatus().name(),
                h.getNewStatus().name(),
                includeInternal ? h.getChangedBy() : null,
                includeInternal ? h.getNote() : null,
                h.getChangedAt());
    }
}
