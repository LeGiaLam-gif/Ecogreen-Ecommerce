package com.example.backend.dto;

/** "Pending" means status PENDING_PAYMENT: the order was placed and is waiting for its payment. */
public record PendingOrdersResponse(String status, long count) {
}
