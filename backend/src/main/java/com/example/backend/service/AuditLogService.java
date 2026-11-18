package com.example.backend.service;

import com.example.backend.entity.AuditLog;
import com.example.backend.entity.OrderStatus;
import com.example.backend.event.OrderStatusChangedEvent;
import com.example.backend.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAdminMutation(Long actorUserId, String method, String path, int responseStatus) {
        try {
            String[] segments = path.split("/");
            String resourceType = segments.length > 4 ? segments[4] : "admin";
            String resourceId = segments.length > 5 ? segments[5] : null;
            repository.save(new AuditLog(actorUserId, "ADMIN_API_" + method, resourceType, resourceId,
                    method, truncate(path, 500), responseStatus, null));
        } catch (RuntimeException e) {
            log.error("Could not persist admin audit log for {}", path, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOrderStatusChange(OrderStatusChangedEvent event) {
        try {
            String oldStatus = statusName(event.oldStatus());
            String newStatus = statusName(event.newStatus());
            String details = "{\"oldStatus\":\"" + oldStatus + "\",\"newStatus\":\"" + newStatus + "\"}";
            repository.save(new AuditLog(event.actorUserId(), "ORDER_STATUS_CHANGED", "orders",
                    String.valueOf(event.orderId()), null, null, null, details));
        } catch (RuntimeException e) {
            log.error("Could not persist order status audit log for order {}", event.orderId(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDescIdDesc(pageable);
    }

    public static Pageable pageable(Integer page, Integer size) {
        return com.example.backend.api.ApiPaging.of(page, size, "createdAt,desc",
                java.util.Map.of("createdAt", "createdAt"), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static String statusName(OrderStatus status) {
        return status == null ? "null" : status.name();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
