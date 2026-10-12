package com.example.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stock of one product: {@code quantityOnHand} is what is physically in the warehouse, {@code reservedQuantity} is the part
 * held for unpaid (PENDING_PAYMENT) orders. Available stock is {@code onHand - reserved}; it is computed when read and never
 * stored. The invariants {@code 0 <= reserved <= onHand} are DB CHECKs (V30).
 *
 * <p>{@code productId} is a plain column on purpose (no relation): inventory is read and locked by id, and loading a Product
 * for every stock operation would cost a join and a second row. The foreign key to products exists only if V30 was applied
 * (see the apply rule in V30). Rows are changed only by {@code InventoryService}, always under a pessimistic row lock.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Inventory() {
        // JPA
    }

    public Inventory(Long productId, int quantityOnHand, int reservedQuantity) {
        this.productId = productId;
        this.quantityOnHand = quantityOnHand;
        this.reservedQuantity = reservedQuantity;
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    /** Units that can currently be sold. Not a getter on purpose: it is derived, not a mapped property. */
    public int available() {
        return quantityOnHand - reservedQuantity;
    }

    public Long getProductId() { return productId; }

    public int getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(int quantityOnHand) { this.quantityOnHand = quantityOnHand; }

    public int getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(int reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
