package com.example.backend.dto;

import com.example.backend.repository.InventoryRepository.InventoryRow;

/**
 * One low-stock product. It carries only the AVAILABLE quantity: on-hand and reserved stay in the
 * {@code /api/v1/admin/inventory} responses (inventory:view), as the B03 contract requires.
 */
public record LowStockItemResponse(Long productId, String productName, String sku, int available) {

    public static LowStockItemResponse from(InventoryRow row) {
        return new LowStockItemResponse(row.getProductId(), row.getProductName(), row.getSku(),
                row.getQuantityOnHand() - row.getReservedQuantity());
    }
}
