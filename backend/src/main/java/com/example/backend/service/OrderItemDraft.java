package com.example.backend.service;

import java.math.BigDecimal;

/**
 * One line of an order about to be created: the product, the quantity and the unit price to snapshot into order_items.
 * The caller (checkout) reads the price from the database; it never comes from the client.
 */
public record OrderItemDraft(Long productId, int quantity, BigDecimal unitPrice) {
}
