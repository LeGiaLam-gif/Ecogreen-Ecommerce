package com.example.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row per status change of a return (append-only), so the complete request and decision history is kept even though
 * {@code returns.admin_note} only holds the latest note. Written only by ReturnService.
 *
 * <p>{@code oldStatus} is null for the first row of a return. {@code changedBy} is the acting user's id (the customer for the
 * first row, staff afterwards); rows back-filled by V60 for returns that existed before B07 have no actor.
 */
@Entity
@Table(name = "return_status_history")
public class ReturnStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private Return returnRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20)
    private ReturnStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private ReturnStatus newStatus;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Return getReturnRequest() { return returnRequest; }
    public void setReturnRequest(Return returnRequest) { this.returnRequest = returnRequest; }

    public ReturnStatus getOldStatus() { return oldStatus; }
    public void setOldStatus(ReturnStatus oldStatus) { this.oldStatus = oldStatus; }

    public ReturnStatus getNewStatus() { return newStatus; }
    public void setNewStatus(ReturnStatus newStatus) { this.newStatus = newStatus; }

    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getChangedAt() { return changedAt; }
}
