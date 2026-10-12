package com.example.backend.service.inventory;

import com.example.backend.entity.InventoryTransaction;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.InventoryRepository;
import com.example.backend.repository.InventoryRepository.InventoryRow;
import com.example.backend.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B03 admin read side: parameter validation, LIKE escaping, 404s, paging order. Mocked repositories, no database. */
class InventoryAdminServiceTest {

    private InventoryRepository inventories;
    private InventoryTransactionRepository ledger;
    private InventoryService inventoryService;
    private InventoryAdminService service;

    @BeforeEach
    void setUp() {
        inventories = mock(InventoryRepository.class);
        ledger = mock(InventoryTransactionRepository.class);
        inventoryService = mock(InventoryService.class);
        service = new InventoryAdminService(inventories, ledger, inventoryService);
        when(inventories.search(anyString(), anyBoolean(), anyInt(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
    }

    static InventoryRow row(long productId, int onHand, int reserved) {
        return new InventoryRow() {
            public Long getProductId() { return productId; }
            public String getProductName() { return "Sản phẩm " + productId; }
            public String getSku() { return null; }
            public Integer getQuantityOnHand() { return onHand; }
            public Integer getReservedQuantity() { return reserved; }
            public LocalDateTime getUpdatedAt() { return null; }
        };
    }

    @Test
    void list_withoutFilters_usesMatchAllPattern_andTheDefaultThreshold() {
        service.list(null, false, null, InventoryAdminService.pageable(null, null));

        verify(inventories).search(eq("%"), eq(false), eq(10), any(Pageable.class));
    }

    @Test
    void list_lowStock_passesThresholdAndFlag() {
        service.list("  Bình ", true, 3, InventoryAdminService.pageable(0, 20));

        verify(inventories).search(eq("%bình%"), eq(true), eq(3), any(Pageable.class));
    }

    @Test
    void list_keyword_escapesLikeWildcardsAndTheEscapeCharacter() {
        service.list("50%_off!", false, null, InventoryAdminService.pageable(0, 20));

        verify(inventories).search(eq("%50!%!_off!!%"), eq(false), eq(10), any(Pageable.class));
    }

    @Test
    void list_invalidThresholdOrTooLongKeyword_isRejected_beforeAnyQuery() {
        assertThrows(BadRequestException.class, () -> service.list(null, true, -1, InventoryAdminService.pageable(0, 20)));
        assertThrows(BadRequestException.class, () -> service.list(null, true, 1_000_001, InventoryAdminService.pageable(0, 20)));
        assertThrows(BadRequestException.class, () -> service.list("x".repeat(101), false, null, InventoryAdminService.pageable(0, 20)));
        verify(inventories, never()).search(anyString(), anyBoolean(), anyInt(), any(Pageable.class));
    }

    @Test
    void pageable_clampsSize_andLedgerIsNewestFirst() {
        assertEquals(100, InventoryAdminService.pageable(0, 1000).getPageSize());
        assertEquals(20, InventoryAdminService.pageable(-5, null).getPageSize());
        assertEquals(0, InventoryAdminService.pageable(-5, null).getPageNumber());

        Pageable ledgerPage = InventoryAdminService.ledgerPageable(2, 50);
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")), ledgerPage.getSort());
        assertEquals(2, ledgerPage.getPageNumber());
    }

    @Test
    void get_unknownProduct_is404() {
        when(inventories.findRow(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(9L));
    }

    @Test
    void ledger_unknownProduct_is404_andTheLedgerIsNotQueried() {
        when(inventories.existsById(9L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.ledger(9L, InventoryAdminService.ledgerPageable(0, 20)));
        verify(ledger, never()).findByProductId(any(), any());
    }

    @Test
    void ledger_knownProduct_returnsThatProductsPage() {
        when(inventories.existsById(7L)).thenReturn(true);
        PageImpl<InventoryTransaction> page = new PageImpl<>(List.of());
        Pageable pageable = InventoryAdminService.ledgerPageable(0, 20);
        when(ledger.findByProductId(7L, pageable)).thenReturn(page);

        assertSame(page, service.ledger(7L, pageable));
    }

    @Test
    void adjust_delegatesToTheInventoryService_andReturnsTheNewRow() {
        InventoryRow after = row(7L, 12, 2);
        when(inventories.findRow(7L)).thenReturn(Optional.of(after));

        InventoryRow result = service.adjust(7L, 2, "stock count", 42L);

        verify(inventoryService).adjust(7L, 2, "stock count", 42L);
        assertSame(after, result);
    }
}
