package com.example.backend.dto;

import com.example.backend.entity.Refund;
import com.example.backend.entity.Return;
import com.example.backend.entity.ReturnImage;
import com.example.backend.entity.ReturnItem;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.entity.ReturnStatusHistory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A return as the API shows it (B07). Built by ReturnService from data it loaded in batches, so building a page of returns
 * touches no lazy association. {@code allowedNextStatuses} is filled for staff only (empty for the customer).
 *
 * <p>A return created before B07 covered a whole order: its {@code items} list is empty.
 */
public class ReturnDto {

    public static class ItemDto {
        public Long orderItemId;
        public Long productId;
        public String productName;
        public int quantity;
        public BigDecimal unitPrice;

        static ItemDto from(ReturnItem item) {
            ItemDto dto = new ItemDto();
            dto.orderItemId = item.getOrderItem().getId();
            dto.productId = item.getOrderItem().getProduct().getId();
            dto.productName = item.getOrderItem().getProduct().getName();
            dto.quantity = item.getQuantity();
            dto.unitPrice = item.getOrderItem().getPrice();
            return dto;
        }
    }

    public static class RefundDto {
        public String status;
        public BigDecimal amount;

        static RefundDto from(Refund refund) {
            RefundDto dto = new RefundDto();
            dto.status = refund.getStatus().name();
            dto.amount = refund.getAmount();
            return dto;
        }
    }

    /** One step of the decision trail of a return (single-return view only). */
    public static class HistoryDto {
        public String oldStatus;
        public String newStatus;
        public Long changedBy;
        public String note;
        public LocalDateTime changedAt;

        public static HistoryDto from(ReturnStatusHistory h) {
            HistoryDto dto = new HistoryDto();
            dto.oldStatus = h.getOldStatus() != null ? h.getOldStatus().name() : null;
            dto.newStatus = h.getNewStatus().name();
            dto.changedBy = h.getChangedBy();
            dto.note = h.getNote();
            dto.changedAt = h.getChangedAt();
            return dto;
        }
    }

    /** Shown when a return is REFUNDED but no refund row exists (the payment was not collected online). */
    public static final String NO_REFUND_MESSAGE = "Không có khoản thanh toán trực tuyến nào cần hoàn lại.";

    public Long id;
    public Long orderId;
    public Long userId;
    public String username;
    public String status;
    public String reason;
    public String description;
    public String adminNote;
    public List<ItemDto> items;
    public List<String> images;
    /** The refund record of this return, or null when none was created. */
    public RefundDto refund;
    /** Set only when the return is REFUNDED without a refund row; explains why there is no money to send back. */
    public String refundMessage;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    /** Server-computed (staff view): the statuses staff may move this return to. Empty for the customer. */
    public List<String> allowedNextStatuses = List.of();
    /** The complete status trail, oldest first. Filled by GET /returns/{id} and by the staff status change; empty in lists. */
    public List<HistoryDto> history = List.of();

    public static ReturnDto of(Return r, List<ReturnItem> items, List<ReturnImage> images, Refund refund,
                               List<String> allowedNextStatuses) {
        ReturnDto dto = new ReturnDto();
        dto.id = r.getId();
        dto.orderId = r.getOrderId();
        dto.userId = r.getUserId();
        dto.username = r.getUser() != null ? r.getUser().getUsername() : null;
        dto.status = r.getStatus().name();
        dto.reason = r.getReason();
        dto.description = r.getDescription() != null ? r.getDescription() : "";
        dto.adminNote = r.getAdminNote() != null ? r.getAdminNote() : "";
        dto.items = items.stream().map(ItemDto::from).toList();
        dto.images = images.stream().map(ReturnImage::getImageUrl).toList();
        dto.refund = refund != null ? RefundDto.from(refund) : null;
        if (refund == null && r.getStatus() == ReturnStatus.REFUNDED) {
            dto.refundMessage = NO_REFUND_MESSAGE;
        }
        dto.createdAt = r.getCreatedAt();
        dto.updatedAt = r.getUpdatedAt();
        dto.allowedNextStatuses = allowedNextStatuses;
        return dto;
    }
}
