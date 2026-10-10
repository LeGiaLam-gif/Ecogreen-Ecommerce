package com.example.backend.dto;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class OrderResponse {
    public Long id;
    public Long userId;
    public String customerName;
    public String customerPhone;
    public String shippingAddress;
    public BigDecimal subtotal;
    public BigDecimal discountAmount;
    public BigDecimal shippingFee;
    public String discountCode;
    public BigDecimal totalPrice;
    public String status;
    public LocalDateTime createdAt;
    public List<OrderItemResponse> items;
    /** Server-computed (staff view): the statuses this caller may move the order to. Empty for a non-staff caller. */
    public List<String> allowedNextStatuses = List.of();
    /** Server-computed (owner view): true when the caller owns the order and it can still be cancelled by the owner. */
    public boolean canCancel;

    /** Legacy/plain view: no caller-specific flags. {@code items} must already carry their product (see OrderItemRepository). */
    public static OrderResponse from(Order o, List<OrderItem> items) {
        return from(o, items, List.of(), false);
    }

    public static OrderResponse from(Order o, List<OrderItem> items, List<String> allowedNextStatuses, boolean canCancel) {
        OrderResponse r = new OrderResponse();
        r.id = o.getId();
        r.userId = o.getUserId(); // never touches the lazy user proxy
        r.customerName = o.getCustomerName();
        r.customerPhone = o.getCustomerPhone();
        r.shippingAddress = o.getShippingAddress();
        r.subtotal = o.getSubtotal();
        r.discountAmount = o.getDiscountAmount();
        r.shippingFee = o.getShippingFee();
        r.discountCode = o.getDiscountCode();
        r.totalPrice = o.getTotalPrice();
        r.status = o.getStatus().name();
        r.createdAt = o.getCreatedAt();
        r.items = items.stream().map(OrderItemResponse::from).collect(Collectors.toList());
        r.allowedNextStatuses = allowedNextStatuses;
        r.canCancel = canCancel;
        return r;
    }
}
