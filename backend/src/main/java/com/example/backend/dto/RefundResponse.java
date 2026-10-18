package com.example.backend.dto;

import com.example.backend.entity.Refund;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(Long id, Long orderId, BigDecimal amount, String status, String reason,
                             String providerTransactionId, LocalDateTime createdAt, LocalDateTime refundedAt) {

    public static RefundResponse from(Refund r) {
        return new RefundResponse(r.getId(), r.getOrder().getId(), r.getAmount(), r.getStatus().name(), r.getReason(),
                r.getProviderTransactionId(), r.getCreatedAt(), r.getRefundedAt());
    }
}
