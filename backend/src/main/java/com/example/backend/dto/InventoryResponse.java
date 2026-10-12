package com.example.backend.dto;

import com.example.backend.repository.InventoryRepository.InventoryRow;

import java.time.LocalDateTime;

/**
 * Admin view of one product's stock. It shows {@code reservedQuantity}, which is internal: it appears ONLY in the
 * {@code /api/v1/admin/inventory} responses (inventory:view) and never in a public or customer response.
 */
public record InventoryResponse(
        Long productId,
        String productName,
        String sku,
        int quantityOnHand,
        int reservedQuantity,
        int available,
        LocalDateTime updatedAt) {

    public static InventoryResponse from(InventoryRow row) {
        int onHand = row.getQuantityOnHand();
        int reserved = row.getReservedQuantity();
        return new InventoryResponse(row.getProductId(), row.getProductName(), row.getSku(), onHand, reserved,
                onHand - reserved, row.getUpdatedAt());
    }
}
