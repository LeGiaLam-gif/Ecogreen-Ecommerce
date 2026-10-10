package com.example.backend.service.inventory;

/**
 * Port through which the order module changes stock. B05 owns this interface; B03 (Inventory) supplies the real
 * reservation model and removes {@link LegacyStockInventoryGateway}.
 *
 * <p>All mutating methods must run inside the caller's transaction, so an order and its stock movements commit or roll
 * back together. {@code refType} and {@code refId} identify what caused the movement (for example {@code "ORDER"} and the
 * order id); the legacy adapter ignores them, B03 will record them.
 */
public interface InventoryGateway {

    /**
     * Take {@code quantity} units out of the available stock for a new order.
     *
     * @throws InsufficientStockException when fewer than {@code quantity} units are available (nothing is changed)
     */
    void reserve(Long productId, int quantity, String refType, Long refId);

    /** Give back stock that was only reserved (the order was cancelled before it was committed). */
    void release(Long productId, int quantity, String refType, Long refId);

    /** Turn a reservation into a final sale (the order was paid, or accepted as cash on delivery). */
    void commit(Long productId, int quantity, String refType, Long refId);

    /**
     * Put back stock that was already committed (a paid order was cancelled). Never call {@link #release} for this case:
     * a real reservation model would then release a reservation that no longer exists.
     */
    void restock(Long productId, int quantity, String refType, Long refId);

    /** Units that can currently be sold. */
    int available(Long productId);
}
