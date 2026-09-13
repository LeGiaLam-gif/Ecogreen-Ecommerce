package com.example.backend.dto;

import com.example.backend.entity.OrderItem;
import java.math.BigDecimal;

public class OrderItemResponse {
    public Long id;
    public Long productId;
    public String productName;
    public String productImage;
    public int quantity;
    public BigDecimal price; // price at the time of purchase

    public static OrderItemResponse from(OrderItem item) {
        OrderItemResponse r = new OrderItemResponse();
        r.id = item.getId();
        r.productId = item.getProduct().getId();
        r.productName = item.getProduct().getName();
        r.productImage = item.getProduct().getImage();
        r.quantity = item.getQuantity();
        r.price = item.getPrice();
        return r;
    }
}
