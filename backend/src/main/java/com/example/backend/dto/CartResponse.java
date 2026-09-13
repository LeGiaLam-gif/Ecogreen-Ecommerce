package com.example.backend.dto;

import com.example.backend.entity.CartItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class CartResponse {
    public Long cartId;
    public List<CartItemResponse> items;
    public BigDecimal totalPrice;
    public int totalItems;

    public static CartResponse from(Long cartId, List<CartItem> items) {
        CartResponse r = new CartResponse();
        r.cartId = cartId;
        r.items = items.stream().map(CartItemResponse::from).collect(Collectors.toList());
        r.totalPrice = r.items.stream().map(i -> i.subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.totalItems = items.stream().mapToInt(CartItem::getQuantity).sum();
        return r;
    }
}
