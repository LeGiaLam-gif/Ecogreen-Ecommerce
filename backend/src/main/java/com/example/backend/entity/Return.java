package com.example.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A customer's request to send back some of the items of a DELIVERED order (B07). The goods come from {@link ReturnItem}
 * rows and the photos from {@link ReturnImage} rows; staff move the return through {@link ReturnStatus} and ReturnService
 * keeps the order, the stock and the refund in step with it.
 *
 * <p>Rows created before B07 (table {@code return_requests}, renamed to {@code returns} by V60) covered a whole order: they
 * have no {@link ReturnItem}s, so they can be approved or rejected but never restocked or refunded.
 *
 * <p>The JPA entity name stays {@code ReturnRequest} on purpose: existing JPQL ({@code UserRepository.countReturnRequestsOf},
 * owned by B08) refers to it, and the table is what changed.
 */
@Entity(name = "ReturnRequest")
@Table(name = "returns")
public class Return {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Read-only mirror of the order_id foreign key, so a list can expose it without touching the lazy {@link #order} proxy. */
    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Read-only mirror of the user_id foreign key (see {@link #orderId}). */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    /** Why the customer returns the goods, e.g. "Sản phẩm bị lỗi / vỡ khi vận chuyển". */
    @Column(nullable = false, length = 100)
    private String reason;

    /** The customer's description of the condition of the goods. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Legacy single image link of a return created before B07. Kept so no data is lost; new returns store their photos in
     * {@link ReturnImage} and leave this null. Column removal: B13.
     *
     * @deprecated use {@link ReturnImage}
     */
    @Deprecated
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.PENDING;

    /** The staff note for the customer (required for REJECTED and INFO_REQUESTED). */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    /** The order's id. Reads the mirrored column for a loaded row (no proxy access) and the reference for a new one. */
    public Long getOrderId() {
        if (orderId != null) {
            return orderId;
        }
        return order != null ? order.getId() : null;
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    /** The owner's id (see {@link #getOrderId()}). */
    public Long getUserId() {
        if (userId != null) {
            return userId;
        }
        return user != null ? user.getId() : null;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** @deprecated legacy single image; see the field. */
    @Deprecated
    public String getImageUrl() { return imageUrl; }

    /** @deprecated legacy single image; new code must not write it. */
    @Deprecated
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public ReturnStatus getStatus() { return status; }
    public void setStatus(ReturnStatus status) { this.status = status; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
