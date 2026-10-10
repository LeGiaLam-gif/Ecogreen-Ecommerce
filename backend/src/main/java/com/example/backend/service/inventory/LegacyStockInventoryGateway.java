package com.example.backend.service.inventory;

import com.example.backend.entity.Product;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TEMPORARY-COMPAT(B05): adapter over the legacy {@code products.stock_quantity} column; remove when B03 (Inventory) ships
 * its reservation model and its own InventoryGateway implementation.
 *
 * <p>Legacy stock is already decremented when an order is created, so: reserve = atomic conditional decrement (this also
 * fixes the legacy overselling race), release and restock = increment, commit = nothing to do, available = the column.
 * There is no separate "reserved" count, which is why release and restock behave the same here; they stay separate in the
 * port so B03 can tell them apart.
 */
@Component
public class LegacyStockInventoryGateway implements InventoryGateway {

    private final ProductRepository productRepository;

    public LegacyStockInventoryGateway(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long productId, int quantity, String refType, Long refId) {
        requirePositive(quantity);
        int updated = productRepository.decreaseStockIfAvailable(productId, quantity);
        if (updated == 0) {
            // Only on the failure path: tell "no such product" apart from "not enough stock".
            if (!productRepository.existsById(productId)) {
                throw new ResourceNotFoundException("Sản phẩm không còn tồn tại.");
            }
            throw new InsufficientStockException(productId, quantity);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long productId, int quantity, String refType, Long refId) {
        increase(productId, quantity);
    }

    @Override
    public void commit(Long productId, int quantity, String refType, Long refId) {
        // Legacy stock was already decremented by reserve(); nothing to commit.
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(Long productId, int quantity, String refType, Long refId) {
        increase(productId, quantity);
    }

    @Override
    @Transactional(readOnly = true)
    public int available(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getStockQuantity)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không còn tồn tại."));
    }

    private void increase(Long productId, int quantity) {
        requirePositive(quantity);
        if (productRepository.increaseStock(productId, quantity) == 0) {
            throw new ResourceNotFoundException("Sản phẩm không còn tồn tại.");
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Số lượng phải lớn hơn 0.");
        }
    }
}
