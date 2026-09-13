package com.example.backend.dto;

import com.example.backend.entity.CartItem;
import java.math.BigDecimal;

public class CartItemResponse {
    public Long id;
    public ProductResponse product;
    public int quantity;
    public BigDecimal subtotal;

    public static CartItemResponse from(CartItem item) {
        CartItemResponse r = new CartItemResponse();
        r.id = item.getId();
        r.product = ProductResponse.from(item.getProduct());
        r.quantity = item.getQuantity();
        r.subtotal = item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return r;
    }
}
