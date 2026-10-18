package com.example.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A discount code (B04): either a percentage ({@link #percentOff}) or a fixed amount ({@link #amountOff}) off the items
 * total of an order. Exactly one of the two is set (CHECK in V40). The code is stored in UPPER CASE and compared
 * case-insensitively. Orders keep the code as plain text (no foreign key), so editing or deleting a discount never changes
 * an order that was already placed.
 */
@Entity
@Table(name = "discounts")
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(name = "percent_off", columnDefinition = "SMALLINT")
    private Integer percentOff;

    @Column(name = "amount_off", precision = 12, scale = 2)
    private BigDecimal amountOff;

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Integer getPercentOff() { return percentOff; }
    public void setPercentOff(Integer percentOff) { this.percentOff = percentOff; }

    public BigDecimal getAmountOff() { return amountOff; }
    public void setAmountOff(BigDecimal amountOff) { this.amountOff = amountOff; }

    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
