package com.example.backend.service.notification;

import com.example.backend.config.NotificationAsyncConfig;
import com.example.backend.entity.NotificationType;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.event.OrderCancelledEvent;
import com.example.backend.event.OrderPlacedEvent;
import com.example.backend.event.OrderStatusChangedEvent;
import com.example.backend.event.ReturnCreatedEvent;
import com.example.backend.event.ReturnStatusChangedEvent;
import com.example.backend.repository.NotificationSourceRepository;
import com.example.backend.repository.NotificationSourceRepository.OrderRow;
import com.example.backend.repository.NotificationSourceRepository.ReturnRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the events other modules publish into customer notifications (B10), with the same mapping as B09's
 * {@code AnalyticsEventListener}. Every handler:
 * <ul>
 *   <li>runs {@code AFTER_COMMIT} with {@code fallbackExecution = false}, so a rolled-back transaction notifies nobody;</li>
 *   <li>is {@code @Async} on {@code notificationExecutor}, so the lookups below and the email never run on the request thread;</li>
 *   <li>reads what the event lacks (owner, total, note) through projections in its own read-only {@code REQUIRES_NEW}
 *       transaction, so no entity is lazy-loaded outside a session;</li>
 *   <li>cannot throw: a failure is logged (class name only) and the business flow is untouched.</li>
 * </ul>
 *
 * <pre>
 * OrderPlacedEvent                          -> ORDER_CREATED
 * OrderStatusChangedEvent newStatus=PAID    -> PAYMENT_SUCCESS
 * OrderStatusChangedEvent newStatus=SHIPPED -> ORDER_SHIPPED
 * OrderStatusChangedEvent newStatus=DELIVERED (not from RETURN_REQUESTED) -> ORDER_DELIVERED
 * OrderCancelledEvent                       -> ORDER_CANCELLED
 * ReturnCreatedEvent                        -> RETURN_CREATED
 * ReturnStatusChangedEvent APPROVED / REJECTED / INFO_REQUESTED / REFUNDED -> RETURN_APPROVED / _REJECTED / _INFO_REQUESTED / _REFUNDED
 * </pre>
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notifications;
    private final NotificationSourceRepository sources;
    private final TransactionTemplate readOnlyNew;

    public NotificationEventListener(NotificationService notifications, NotificationSourceRepository sources,
                                     PlatformTransactionManager transactionManager) {
        this.notifications = notifications;
        this.sources = sources;
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setReadOnly(true);
        this.readOnlyNew = read;
    }

    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        safely("ORDER_CREATED", () -> {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("orderId", event.orderId());
            ctx.put("orderTotal", event.total());
            notifications.deliver(event.userId(), NotificationType.ORDER_CREATED, ctx, "order:" + event.orderId() + ":CREATED");
        });
    }

    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        safely("order status", () -> {
            NotificationType type = typeFor(event.oldStatus(), event.newStatus());
            if (type == null) {
                return;
            }
            OrderRow order = readOnlyNew.execute(status -> sources.findOrder(event.orderId()).orElse(null));
            if (order == null) {
                log.warn("Notification {} skipped: order {} not found", type, event.orderId());
                return;
            }
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("orderId", event.orderId());
            ctx.put("orderTotal", order.getTotalPrice());
            notifications.deliver(order.getUserId(), type, ctx, "order:" + event.orderId() + ":" + event.newStatus().name());
        });
    }

    /** The notification an order status change triggers, or null when it triggers none. */
    static NotificationType typeFor(OrderStatus oldStatus, OrderStatus newStatus) {
        if (newStatus == null) {
            return null;
        }
        return switch (newStatus) {
            case PAID -> NotificationType.PAYMENT_SUCCESS;
            case SHIPPED -> NotificationType.ORDER_SHIPPED;
            // DELIVERED after RETURN_REQUESTED means the return was rejected, not a new delivery
            case DELIVERED -> oldStatus == OrderStatus.RETURN_REQUESTED ? null : NotificationType.ORDER_DELIVERED;
            default -> null;
        };
    }

    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        safely("ORDER_CANCELLED", () -> {
            OrderRow order = readOnlyNew.execute(status -> sources.findOrder(event.orderId()).orElse(null));
            if (order == null) {
                log.warn("Notification ORDER_CANCELLED skipped: order {} not found", event.orderId());
                return;
            }
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("orderId", event.orderId());
            ctx.put("orderTotal", order.getTotalPrice());
            ctx.put("refundRequired", event.refundRequired());
            notifications.deliver(order.getUserId(), NotificationType.ORDER_CANCELLED, ctx, "order:" + event.orderId() + ":CANCELLED");
        });
    }

    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReturnCreated(ReturnCreatedEvent event) {
        safely("RETURN_CREATED", () -> {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("returnId", event.returnId());
            ctx.put("orderId", event.orderId());
            notifications.deliver(event.userId(), NotificationType.RETURN_CREATED, ctx, "return:" + event.returnId() + ":CREATED");
        });
    }

    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReturnStatusChanged(ReturnStatusChangedEvent event) {
        safely("return status", () -> {
            NotificationType type = typeFor(event.newStatus());
            if (type == null) {
                return;
            }
            ReturnRow ret = readOnlyNew.execute(status -> sources.findReturn(event.returnId()).orElse(null));
            if (ret == null) {
                log.warn("Notification {} skipped: return {} not found", type, event.returnId());
                return;
            }
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("returnId", event.returnId());
            ctx.put("orderId", ret.getOrderId());
            ctx.put("note", ret.getAdminNote());
            notifications.deliver(ret.getUserId(), type, ctx, "return:" + event.returnId() + ":" + event.newStatus().name());
        });
    }

    /** The notification a return status triggers, or null (PENDING, RETURNED and unknown statuses send nothing). */
    static NotificationType typeFor(ReturnStatus newStatus) {
        if (newStatus == null) {
            return null;
        }
        return switch (newStatus) {
            case APPROVED -> NotificationType.RETURN_APPROVED;
            case REJECTED -> NotificationType.RETURN_REJECTED;
            case INFO_REQUESTED -> NotificationType.RETURN_INFO_REQUESTED;
            case REFUNDED -> NotificationType.RETURN_REFUNDED;
            default -> null;
        };
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Notification listener for {} failed: {}", what, e.getClass().getSimpleName());
        }
    }
}
