package com.example.backend.entity;

/**
 * Kinds of customer notification (B10). The name is stored in {@code notifications.type}; V90 only checks the FORMAT
 * (UPPER_SNAKE_CASE), so a new type needs a template and an event, not DDL. Source event and template: see the B10 contract.
 */
public enum NotificationType {
    ORDER_CREATED,
    PAYMENT_SUCCESS,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_CANCELLED,
    RETURN_CREATED,
    RETURN_APPROVED,
    RETURN_REJECTED,
    RETURN_INFO_REQUESTED,
    RETURN_REFUNDED
}
