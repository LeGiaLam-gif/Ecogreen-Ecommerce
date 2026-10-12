package com.example.backend.dto;

import com.example.backend.entity.CartItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CartResponse {
    public Long cartId;
    public List<CartItemResponse> items;
    public BigDecimal totalPrice;
    public int totalItems;

    /** {@code availableByProductId} comes from ONE batched inventory query (CartService.availabilityFor). */
    public static CartResponse from(Long cartId, List<CartItem> items, Map<Long, Integer> availableByProductId) {
        CartResponse r = new CartResponse();
        r.cartId = cartId;
        r.items = items.stream()
                .map(i -> CartItemResponse.from(i, availableByProductId.getOrDefault(i.getProduct().getId(), 0)))
                .collect(Collectors.toList());
        r.totalPrice = r.items.stream().map(i -> i.subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.totalItems = items.stream().mapToInt(CartItem::getQuantity).sum();
        return r;
    }
}
