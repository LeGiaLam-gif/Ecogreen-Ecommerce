package com.example.backend.service.inventory;

import com.example.backend.entity.Product;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B05: the TEMPORARY-COMPAT adapter over products.stock_quantity, with a mocked repository. That the SQL itself is atomic
 * (two simultaneous reserves of the last unit, exactly one wins) needs PostgreSQL and is a separate gated test.
 */
class LegacyStockInventoryGatewayTest {

    private ProductRepository products;
    private LegacyStockInventoryGateway gateway;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        gateway = new LegacyStockInventoryGateway(products);
    }

    @Test
    void reserve_enoughStock_usesTheAtomicConditionalDecrement() {
        when(products.decreaseStockIfAvailable(5L, 2)).thenReturn(1);

        gateway.reserve(5L, 2, "ORDER", 9L);

        verify(products).decreaseStockIfAvailable(5L, 2);
        verify(products, never()).existsById(any());
    }

    @Test
    void reserve_notEnoughStock_throwsInsufficientStock_andCarriesTheProductAndQuantity() {
        when(products.decreaseStockIfAvailable(5L, 3)).thenReturn(0);
        when(products.existsById(5L)).thenReturn(true);

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> gateway.reserve(5L, 3, "ORDER", 9L));

        assertEquals(5L, ex.getProductId());
        assertEquals(3, ex.getRequested());
    }

    @Test
    void reserve_unknownProduct_throwsNotFound_notInsufficientStock() {
        when(products.decreaseStockIfAvailable(5L, 1)).thenReturn(0);
        when(products.existsById(5L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> gateway.reserve(5L, 1, "ORDER", 9L));
    }

    @Test
    void reserve_nonPositiveQuantity_isRejected_andTouchesNothing() {
        assertThrows(BadRequestException.class, () -> gateway.reserve(5L, 0, "ORDER", 9L));
        assertThrows(BadRequestException.class, () -> gateway.reserve(5L, -1, "ORDER", 9L));
        verifyNoInteractions(products);
    }

    @Test
    void release_and_restock_bothAddTheQuantityBack() {
        when(products.increaseStock(5L, 2)).thenReturn(1);

        gateway.release(5L, 2, "ORDER", 9L);
        gateway.restock(5L, 2, "ORDER", 9L);

        verify(products, times(2)).increaseStock(5L, 2);
    }

    @Test
    void release_unknownProduct_throwsNotFound() {
        when(products.increaseStock(5L, 2)).thenReturn(0);

        assertThrows(ResourceNotFoundException.class, () -> gateway.release(5L, 2, "ORDER", 9L));
        assertThrows(ResourceNotFoundException.class, () -> gateway.restock(5L, 2, "ORDER", 9L));
    }

    @Test
    void release_nonPositiveQuantity_isRejected() {
        assertThrows(BadRequestException.class, () -> gateway.release(5L, 0, "ORDER", 9L));
        assertThrows(BadRequestException.class, () -> gateway.restock(5L, -3, "ORDER", 9L));
        verifyNoInteractions(products);
    }

    @Test
    void commit_isANoOp_becauseLegacyStockWasAlreadyDecrementedAtReserveTime() {
        gateway.commit(5L, 2, "ORDER", 9L);

        verifyNoInteractions(products);
    }

    @Test
    void available_readsTheStockColumn() {
        Product product = new Product();
        product.setStockQuantity(7);
        when(products.findById(5L)).thenReturn(Optional.of(product));

        assertEquals(7, gateway.available(5L));
    }

    @Test
    void available_unknownProduct_throwsNotFound() {
        when(products.findById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> gateway.available(5L));
    }
}
