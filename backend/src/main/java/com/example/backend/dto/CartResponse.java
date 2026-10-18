package com.example.backend.dto;

import com.example.backend.entity.CartItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The caller's cart on /api/v1/cart. {@code subtotal} is the items total only: discount and shipping come from the checkout preview. */
public class CartResponse {
    /** Null for a user who has no cart row yet (reading a cart never creates one). */
    public Long cartId;
    public List<CartItemResponse> items;
    public BigDecimal subtotal;
    public int totalItems;

    /** {@code availableByProductId} comes from ONE batched inventory query (CartService.availabilityFor). */
    public static CartResponse from(Long cartId, List<CartItem> items, Map<Long, Integer> availableByProductId) {
        CartResponse r = new CartResponse();
        r.cartId = cartId;
        r.items = items.stream()
                .map(i -> CartItemResponse.from(i, availableByProductId.getOrDefault(i.getProduct().getId(), 0)))
                .collect(Collectors.toList());
        r.subtotal = r.items.stream().map(i -> i.lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.totalItems = items.stream().mapToInt(CartItem::getQuantity).sum();
        return r;
    }

    public static CartResponse empty() {
        return from(null, List.of(), Map.of());
    }
}
