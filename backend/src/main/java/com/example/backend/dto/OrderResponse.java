package com.example.backend.dto;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.Payment;
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
    public String deliveryNote;
    public BigDecimal totalPrice;
    public String status;
    public String paymentMethod;
    public String paymentStatus;
    public LocalDateTime createdAt;
    public List<OrderItemResponse> items;

    public static OrderResponse from(Order o, List<OrderItem> items) {
        return from(o, items, null);
    }

    public static OrderResponse from(Order o, List<OrderItem> items, Payment payment) {
        OrderResponse r = new OrderResponse();
        r.id = o.getId();
        r.userId = o.getUser().getId();
        r.customerName = o.getCustomerName();
        r.customerPhone = o.getCustomerPhone();
        r.shippingAddress = o.getShippingAddress();
        r.deliveryNote = o.getDeliveryNote();
        r.totalPrice = o.getTotalPrice();
        r.status = o.getStatus().name();
        r.createdAt = o.getCreatedAt();
        r.items = items.stream().map(OrderItemResponse::from).collect(Collectors.toList());
        if (payment != null) {
            r.paymentMethod = payment.getPaymentMethod();
            r.paymentStatus = payment.getStatus().name();
        }
        return r;
    }
}
