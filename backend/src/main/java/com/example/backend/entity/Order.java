package com.example.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Holds a shipping/customer-info SNAPSHOT taken at checkout time, so a
 * historical order never depends on the user's current profile data.
 *
 * <p>B05 rule: {@code status} is changed ONLY by OrderService.transitionStatus (it validates the transition, applies the
 * inventory side effect and writes the history row). Nothing else may call {@link #setStatus}.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Read-only mirror of the user_id foreign key, so an order list can expose the owner's id without touching the lazy
     * {@link #user} proxy (see {@link #getUserId()}).
     */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "shipping_address", nullable = false, columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    /** Items total before discount and shipping. Invariant: totalPrice = subtotal - discountAmount + shippingFee. */
    @Column(nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    private BigDecimal shippingFee = BigDecimal.ZERO;

    /** Plain text on purpose (no foreign key), so deleting a discount never breaks order history. */
    @Column(name = "discount_code", length = 50)
    private String discountCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

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

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    /**
     * The owner's id. Reads the mirrored column when the order was loaded from the database (no proxy access), and falls
     * back to the {@link #user} reference for an order that has just been created in this session.
     */
    public Long getUserId() {
        if (userId != null) {
            return userId;
        }
        return user != null ? user.getId() : null;
    }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public String getDiscountCode() { return discountCode; }
    public void setDiscountCode(String discountCode) { this.discountCode = discountCode; }

    public OrderStatus getStatus() { return status; }

    /** Only OrderService.transitionStatus may call this (B05 rule, enforced by a source-scan test from Phase 2). */
    public void setStatus(OrderStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
