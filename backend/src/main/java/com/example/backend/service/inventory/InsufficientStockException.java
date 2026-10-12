package com.example.backend.service.inventory;

import com.example.backend.exception.BusinessRuleViolationException;

/**
 * Thrown by {@link InventoryGateway#reserve} when the requested quantity is no longer available.
 *
 * <p>It extends {@link BusinessRuleViolationException}, so the unchanged GlobalExceptionHandler (owned by B01-F1) answers
 * 400 {@code BUSINESS_RULE_VIOLATION} on /api/v1 and a plain 400 {@code {message}} on legacy paths (B03 changed this from the
 * 409 CONFLICT that B05 used). The message names the product by its public catalogue name so the customer knows which line
 * to fix; it never contains a product id, a reserved quantity or any other internal number. The id and the requested
 * quantity are kept as fields for logs and tests only.
 */
public class InsufficientStockException extends BusinessRuleViolationException {

    private final Long productId;
    private final int requested;

    public InsufficientStockException(Long productId, int requested, String productName) {
        super(buildMessage(productName));
        this.productId = productId;
        this.requested = requested;
    }

    private static String buildMessage(String productName) {
        String name = productName == null ? "" : productName.trim();
        return name.isEmpty()
                ? "Một sản phẩm không đủ số lượng tồn kho."
                : "Sản phẩm \"" + name + "\" không đủ số lượng tồn kho.";
    }

    public Long getProductId() { return productId; }

    public int getRequested() { return requested; }
}
