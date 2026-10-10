package com.example.backend.service.inventory;

import com.example.backend.exception.ConflictException;

/**
 * Thrown by {@link InventoryGateway#reserve} when the requested quantity is no longer available.
 *
 * <p>It extends {@link ConflictException} on purpose: GlobalExceptionHandler (owned by B01-F1) already maps that to
 * 409 CONFLICT on /api/v1 and to a 409 {message} on legacy paths, so no handler change is needed. The message names no
 * product, so it is safe to show to a customer.
 */
public class InsufficientStockException extends ConflictException {

    private final Long productId;
    private final int requested;

    public InsufficientStockException(Long productId, int requested) {
        super("Không đủ số lượng tồn kho cho một hoặc nhiều sản phẩm trong đơn hàng.");
        this.productId = productId;
        this.requested = requested;
    }

    public Long getProductId() { return productId; }

    public int getRequested() { return requested; }
}
