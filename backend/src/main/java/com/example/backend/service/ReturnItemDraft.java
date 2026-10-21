package com.example.backend.service;

/** One line a customer wants to return: the order line (order_items.id) and how many units. Validated by ReturnService. */
public record ReturnItemDraft(Long orderItemId, int quantity) {
}
