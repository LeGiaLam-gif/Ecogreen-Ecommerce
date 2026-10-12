package com.example.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row of the append-only stock ledger; written only by {@code InventoryService}, in the same transaction as the change.
 *
 * <p>{@code quantity} is the positive amount for RESERVE / RELEASE / COMMIT / RESTOCK and the signed delta for ADJUST.
 * {@code onHandAfter} / {@code reservedAfter} are the inventory values right after the change: the ledger is reconciled
 * through those two columns (the last row of a product must equal its inventory row), never by summing quantities.
 * {@code referenceType} / {@code referenceId} are both set (for example {@code "ORDER"} and the order id) or both null.
 */
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    /** The same five values are in the chk_inventory_tx_type CHECK (V31). */
    public enum ChangeType { RESERVE, RELEASE, COMMIT, RESTOCK, ADJUST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 10)
    private ChangeType changeType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "on_hand_after", nullable = false)
    private int onHandAfter;

    @Column(name = "reserved_after", nullable = false)
    private int reservedAfter;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(length = 255)
    private String reason;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected InventoryTransaction() {
        // JPA
    }

    public InventoryTransaction(Long productId, ChangeType changeType, int quantity, int onHandAfter, int reservedAfter,
                                String referenceType, Long referenceId, String reason, Long actorUserId) {
        this.productId = productId;
        this.changeType = changeType;
        this.quantity = quantity;
        this.onHandAfter = onHandAfter;
        this.reservedAfter = reservedAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reason = reason;
        this.actorUserId = actorUserId;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public ChangeType getChangeType() { return changeType; }
    public int getQuantity() { return quantity; }
    public int getOnHandAfter() { return onHandAfter; }
    public int getReservedAfter() { return reservedAfter; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public String getReason() { return reason; }
    public Long getActorUserId() { return actorUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
