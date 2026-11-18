package com.example.backend.service;

import com.example.backend.event.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditLogEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditLogEventListener.class);
    private final AuditLogService auditLogService;

    public AuditLogEventListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        try {
            auditLogService.recordOrderStatusChange(event);
        } catch (RuntimeException e) {
            log.error("Could not record order status audit log for order {}", event.orderId(), e);
        }
    }
}
