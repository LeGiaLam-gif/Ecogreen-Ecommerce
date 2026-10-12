package com.example.backend.service.inventory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.backend.entity.Inventory;
import com.example.backend.entity.InventoryTransaction;
import com.example.backend.entity.InventoryTransaction.ChangeType;
import com.example.backend.entity.Product;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.InventoryRepository;
import com.example.backend.repository.InventoryRepository.AvailableRow;
import com.example.backend.repository.InventoryTransactionRepository;
import com.example.backend.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B03: the rules of InventoryService with mocked repositories. What a mock cannot show - that the row lock really serialises
 * two threads, that opposite lock orders do not deadlock, that V30/V31 convert data correctly - is covered by the
 * PostgreSQL-gated tests (InventoryPostgresTest), which are reported as NOT RUN when ECOGREEN_TEST_PG_URL is absent.
 */
class InventoryServiceTest {

    private static final Long PRODUCT = 7L;
    private static final String REF = "ORDER";
    private static final Long ORDER_ID = 100L;

    private InventoryRepository inventories;
    private InventoryTransactionRepository ledger;
    private ProductRepository products;
    private InventoryService service;
    private ListAppender<ILoggingEvent> logs;
    private ch.qos.logback.classic.Logger serviceLogger;

    @BeforeEach
    void setUp() {
        inventories = mock(InventoryRepository.class);
        ledger = mock(InventoryTransactionRepository.class);
        products = mock(ProductRepository.class);
        service = new InventoryService(inventories, ledger, products);

        serviceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(InventoryService.class);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
    }

    private Inventory row(int onHand, int reserved) {
        Inventory inventory = new Inventory(PRODUCT, onHand, reserved);
        when(inventories.findByProductIdForUpdate(PRODUCT)).thenReturn(Optional.of(inventory));
        return inventory;
    }

    private InventoryTransaction onlyLedgerRow() {
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(ledger).save(captor.capture());
        return captor.getValue();
    }

    private void productNamed(String name) {
        Product product = new Product();
        product.setName(name);
        when(products.findById(PRODUCT)).thenReturn(Optional.of(product));
    }

    // ------------------------------------------------------------------ reserve

    @Test
    void reserve_insufficientAvailable_throws_namesTheProduct_andChangesNothing() {
        Inventory inventory = row(5, 4); // available = 1
        productNamed("Túi vải tái chế");

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> service.reserve(PRODUCT, 2, REF, ORDER_ID));

        assertTrue(ex.getMessage().contains("Túi vải tái chế"));
        assertFalse(ex.getMessage().matches(".*\\d.*"), "the message carries no id and no quantity");
        assertEquals(4, inventory.getReservedQuantity());
        assertEquals(5, inventory.getQuantityOnHand());
        verify(ledger, never()).save(any());
    }

    @Test
    void reserve_enoughAvailable_increasesReserved_andWritesOneLedgerRowWithAfterValues() {
        Inventory inventory = row(10, 3);

        service.reserve(PRODUCT, 4, REF, ORDER_ID);

        assertEquals(7, inventory.getReservedQuantity());
        assertEquals(10, inventory.getQuantityOnHand());
        assertEquals(3, inventory.available());
        InventoryTransaction tx = onlyLedgerRow();
        assertEquals(ChangeType.RESERVE, tx.getChangeType());
        assertEquals(4, tx.getQuantity());
        assertEquals(10, tx.getOnHandAfter());
        assertEquals(7, tx.getReservedAfter());
        assertEquals(REF, tx.getReferenceType());
        assertEquals(ORDER_ID, tx.getReferenceId());
    }

    @Test
    void reserve_exactlyTheAvailableQuantity_succeeds() {
        Inventory inventory = row(5, 4);

        service.reserve(PRODUCT, 1, REF, ORDER_ID);

        assertEquals(5, inventory.getReservedQuantity());
        assertEquals(0, inventory.available());
    }

    @Test
    void reserve_isCheckedOnTheLockedRow_notOnAnEarlierRead() {
        row(5, 0);
        service.reserve(PRODUCT, 5, REF, ORDER_ID);

        verify(inventories).findByProductIdForUpdate(PRODUCT);
        verify(inventories, never()).findById(any());
    }

    @Test
    void reserve_productWithoutInventoryRow_failsClosed_andLogsAWarnWithTheProductId() {
        when(inventories.findByProductIdForUpdate(PRODUCT)).thenReturn(Optional.empty());
        productNamed("Bình giữ nhiệt");

        assertThrows(InsufficientStockException.class, () -> service.reserve(PRODUCT, 1, REF, ORDER_ID));

        verify(ledger, never()).save(any());
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("7")),
                "a WARN naming the product id must be logged");
    }

    @Test
    void reserve_replaySameReference_isALoggedNoOp() {
        Inventory inventory = row(10, 2);
        when(ledger.existsByProductIdAndChangeTypeAndReferenceTypeAndReferenceId(PRODUCT, ChangeType.RESERVE, REF, ORDER_ID))
                .thenReturn(true);

        service.reserve(PRODUCT, 3, REF, ORDER_ID);

        assertEquals(2, inventory.getReservedQuantity());
        verify(ledger, never()).save(any());
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.INFO), "a replay is logged");
    }

    @Test
    void reserve_nonPositiveQuantity_orMissingReference_isRejected_andTouchesNothing() {
        assertThrows(BadRequestException.class, () -> service.reserve(PRODUCT, 0, REF, ORDER_ID));
        assertThrows(BadRequestException.class, () -> service.reserve(PRODUCT, -1, REF, ORDER_ID));
        assertThrows(IllegalArgumentException.class, () -> service.reserve(PRODUCT, 1, null, ORDER_ID));
        assertThrows(IllegalArgumentException.class, () -> service.reserve(PRODUCT, 1, REF, null));
        verifyNoInteractions(inventories, ledger);
    }

    // ------------------------------------------------------------------ every operation writes a ledger row

    @Test
    void everyOperation_writesLedgerRow_withAfterValues() {
        Inventory inventory = row(10, 0);

        service.reserve(PRODUCT, 2, REF, 1L);          // on hand 10, reserved 2
        service.release(PRODUCT, 1, REF, 1L);          // 10, 1
        service.commit(PRODUCT, 1, REF, 2L);           // 9, 0
        service.restock(PRODUCT, 3, REF, 3L);          // 12, 0
        service.adjust(PRODUCT, 2, "recount", 7L);     // 14, 0

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(ledger, times(5)).save(captor.capture());
        List<InventoryTransaction> rows = captor.getAllValues();
        List<ChangeType> types = List.of(ChangeType.RESERVE, ChangeType.RELEASE, ChangeType.COMMIT, ChangeType.RESTOCK, ChangeType.ADJUST);
        int[][] expectedAfter = {{10, 2}, {10, 1}, {9, 0}, {12, 0}, {14, 0}};
        int[] expectedQuantity = {2, 1, 1, 3, 2};
        for (int i = 0; i < 5; i++) {
            assertEquals(types.get(i), rows.get(i).getChangeType(), "change type of row " + i);
            assertEquals(expectedQuantity[i], rows.get(i).getQuantity(), "quantity of row " + i);
            assertEquals(expectedAfter[i][0], rows.get(i).getOnHandAfter(), "on_hand_after of row " + i);
            assertEquals(expectedAfter[i][1], rows.get(i).getReservedAfter(), "reserved_after of row " + i);
        }
        // the last row equals the inventory row: the ledger reconciles through the *_after columns
        assertEquals(inventory.getQuantityOnHand(), rows.get(4).getOnHandAfter());
        assertEquals(inventory.getReservedQuantity(), rows.get(4).getReservedAfter());
    }

    // ------------------------------------------------------------------ release / commit / restock

    @Test
    void release_restoresAvailability_andLogs() {
        Inventory inventory = row(10, 6);

        service.release(PRODUCT, 4, REF, ORDER_ID);

        assertEquals(2, inventory.getReservedQuantity());
        assertEquals(10, inventory.getQuantityOnHand());
        assertEquals(8, inventory.available());
        InventoryTransaction tx = onlyLedgerRow();
        assertEquals(ChangeType.RELEASE, tx.getChangeType());
        assertEquals(10, tx.getOnHandAfter());
        assertEquals(2, tx.getReservedAfter());
    }

    @Test
    void release_moreThanReserved_failsTheTransaction_andWritesNothing() {
        Inventory inventory = row(10, 1);

        assertThrows(IllegalStateException.class, () -> service.release(PRODUCT, 2, REF, ORDER_ID));

        assertEquals(1, inventory.getReservedQuantity());
        verify(ledger, never()).save(any());
    }

    @Test
    void commit_reducesOnHandAndReservedTogether_availableUnchanged() {
        Inventory inventory = row(10, 4); // available 6

        service.commit(PRODUCT, 3, REF, ORDER_ID);

        assertEquals(7, inventory.getQuantityOnHand());
        assertEquals(1, inventory.getReservedQuantity());
        assertEquals(6, inventory.available(), "committing a reservation does not change what is still available");
        InventoryTransaction tx = onlyLedgerRow();
        assertEquals(ChangeType.COMMIT, tx.getChangeType());
        assertEquals(7, tx.getOnHandAfter());
        assertEquals(1, tx.getReservedAfter());
    }

    @Test
    void commit_moreThanReserved_failsTheTransaction() {
        row(10, 1);

        assertThrows(IllegalStateException.class, () -> service.commit(PRODUCT, 2, REF, ORDER_ID));
        verify(ledger, never()).save(any());
    }

    @Test
    void restock_increasesOnHand_andLogs_withoutTouchingReserved() {
        Inventory inventory = row(10, 4);

        service.restock(PRODUCT, 2, REF, ORDER_ID);

        assertEquals(12, inventory.getQuantityOnHand());
        assertEquals(4, inventory.getReservedQuantity());
        InventoryTransaction tx = onlyLedgerRow();
        assertEquals(ChangeType.RESTOCK, tx.getChangeType());
        assertEquals(2, tx.getQuantity());
        assertEquals(12, tx.getOnHandAfter());
        assertEquals(4, tx.getReservedAfter());
    }

    @Test
    void releaseCommitRestock_replay_areNoOps() {
        Inventory inventory = row(10, 4);
        for (ChangeType type : List.of(ChangeType.RELEASE, ChangeType.COMMIT, ChangeType.RESTOCK)) {
            when(ledger.existsByProductIdAndChangeTypeAndReferenceTypeAndReferenceId(PRODUCT, type, REF, ORDER_ID))
                    .thenReturn(true);
        }

        service.release(PRODUCT, 1, REF, ORDER_ID);
        service.commit(PRODUCT, 1, REF, ORDER_ID);
        service.restock(PRODUCT, 1, REF, ORDER_ID);

        assertEquals(10, inventory.getQuantityOnHand());
        assertEquals(4, inventory.getReservedQuantity());
        verify(ledger, never()).save(any());
    }

    @Test
    void release_commit_restock_onAProductWithoutRow_areNotFound_andLogAWarn() {
        when(inventories.findByProductIdForUpdate(PRODUCT)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.release(PRODUCT, 1, REF, ORDER_ID));
        assertThrows(ResourceNotFoundException.class, () -> service.commit(PRODUCT, 1, REF, ORDER_ID));
        assertThrows(ResourceNotFoundException.class, () -> service.restock(PRODUCT, 1, REF, ORDER_ID));
        assertEquals(3, logs.list.stream().filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("7")).count());
    }

    // ------------------------------------------------------------------ adjust

    @Test
    void adjust_requiresAReason() {
        row(10, 0);

        assertThrows(BadRequestException.class, () -> service.adjust(PRODUCT, 1, null, 1L));
        assertThrows(BadRequestException.class, () -> service.adjust(PRODUCT, 1, "   ", 1L));
        assertThrows(BadRequestException.class, () -> service.adjust(PRODUCT, 1, "x".repeat(256), 1L));
        verifyNoInteractions(ledger);
        verify(inventories, never()).findByProductIdForUpdate(any());
    }

    @Test
    void adjust_zeroDelta_isRejected() {
        assertThrows(BadRequestException.class, () -> service.adjust(PRODUCT, 0, "recount", 1L));
        verifyNoInteractions(ledger);
    }

    @Test
    void adjust_belowReserved_isRejected_andWritesNothing() {
        Inventory inventory = row(10, 6);

        assertThrows(BusinessRuleViolationException.class, () -> service.adjust(PRODUCT, -5, "damaged", 1L));

        assertEquals(10, inventory.getQuantityOnHand());
        verify(ledger, never()).save(any());
    }

    @Test
    void adjust_downToExactlyReserved_isAllowed() {
        Inventory inventory = row(10, 6);

        service.adjust(PRODUCT, -4, "damaged", 1L);

        assertEquals(6, inventory.getQuantityOnHand());
        assertEquals(0, inventory.available());
    }

    @Test
    void adjust_writesLedgerRow_withSignedDelta_reason_actor_andAfterValues() {
        row(10, 2);

        service.adjust(PRODUCT, -3, "  stock count  ", 42L);

        InventoryTransaction tx = onlyLedgerRow();
        assertEquals(ChangeType.ADJUST, tx.getChangeType());
        assertEquals(-3, tx.getQuantity());
        assertEquals(7, tx.getOnHandAfter());
        assertEquals(2, tx.getReservedAfter());
        assertEquals("stock count", tx.getReason());
        assertEquals(42L, tx.getActorUserId());
        assertNull(tx.getReferenceType());
        assertNull(tx.getReferenceId());
    }

    @Test
    void adjust_productWithoutRow_isNotFound() {
        when(inventories.findByProductIdForUpdate(PRODUCT)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.adjust(PRODUCT, 1, "recount", 1L));
    }

    // ------------------------------------------------------------------ setAvailable (admin product form)

    @Test
    void setAvailable_sameAsLockedAvailable_writesNothing_noLedgerRow() {
        Inventory inventory = row(10, 3); // available 7

        int delta = service.setAvailable(PRODUCT, 7, "Admin product form", 1L);

        assertEquals(0, delta);
        assertEquals(10, inventory.getQuantityOnHand());
        verify(ledger, never()).save(any());
        verify(inventories, never()).save(any());
    }

    @Test
    void setAvailable_computesTheDeltaFromTheLockedValues_notFromWhatTheFormLoaded() {
        // The form loaded available = 10, but customers reserved 3 meanwhile: the locked row says available = 7.
        Inventory inventory = row(10, 3);

        int delta = service.setAvailable(PRODUCT, 12, "Admin product form", 1L);

        assertEquals(5, delta);
        assertEquals(15, inventory.getQuantityOnHand());
        assertEquals(12, inventory.available());
        InventoryTransaction tx = onlyLedgerRow();
        assertEquals(ChangeType.ADJUST, tx.getChangeType());
        assertEquals(5, tx.getQuantity());
        assertEquals("Admin product form", tx.getReason());
        assertEquals(1L, tx.getActorUserId());
    }

    @Test
    void setAvailable_lowering_keepsOnHandAtOrAboveReserved() {
        Inventory inventory = row(10, 3);

        int delta = service.setAvailable(PRODUCT, 0, "Admin product form", 1L);

        assertEquals(-7, delta);
        assertEquals(3, inventory.getQuantityOnHand());
        assertEquals(3, inventory.getReservedQuantity());
    }

    @Test
    void setAvailable_negativeTarget_orMissingReason_isRejected() {
        assertThrows(BadRequestException.class, () -> service.setAvailable(PRODUCT, -1, "Admin product form", 1L));
        assertThrows(BadRequestException.class, () -> service.setAvailable(PRODUCT, 1, "", 1L));
        verifyNoInteractions(inventories, ledger);
    }

    // ------------------------------------------------------------------ initialize

    @Test
    void initialize_positiveStock_createsTheRow_andARestockLedgerRowInitialStock() {
        when(inventories.existsById(PRODUCT)).thenReturn(false);

        service.initialize(PRODUCT, 25, 9L);

        ArgumentCaptor<Inventory> rowCaptor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventories).save(rowCaptor.capture());
        assertEquals(25, rowCaptor.getValue().getQuantityOnHand());
        assertEquals(0, rowCaptor.getValue().getReservedQuantity());
        InventoryTransaction tx = onlyLedgerRow();
        assertEquals(ChangeType.RESTOCK, tx.getChangeType());
        assertEquals(25, tx.getQuantity());
        assertEquals(25, tx.getOnHandAfter());
        assertEquals("Initial stock", tx.getReason());
        assertEquals(9L, tx.getActorUserId());
    }

    @Test
    void initialize_zeroStock_createsTheRow_andAnAdjustOfZero_becauseARestockOfZeroIsForbidden() {
        when(inventories.existsById(PRODUCT)).thenReturn(false);

        service.initialize(PRODUCT, 0, 9L);

        InventoryTransaction tx = onlyLedgerRow();
        assertEquals(ChangeType.ADJUST, tx.getChangeType());
        assertEquals(0, tx.getQuantity());
        assertEquals(0, tx.getOnHandAfter());
    }

    @Test
    void initialize_existingRow_isALoggedNoOp_andNegativeIsRejected() {
        when(inventories.existsById(PRODUCT)).thenReturn(true);

        service.initialize(PRODUCT, 5, 9L);

        verify(inventories, never()).save(any());
        verify(ledger, never()).save(any());
        assertThrows(BadRequestException.class, () -> service.initialize(PRODUCT, -1, 9L));
    }

    // ------------------------------------------------------------------ availability

    @Test
    void availableFor_manyProducts_isOneQuery_andMissingRowsAreZero_withAWarn() {
        // Build the row mocks BEFORE stubbing: creating a mock inside thenReturn(...) breaks Mockito's stubbing.
        AvailableRow first = availableRow(1L, 5);
        AvailableRow second = availableRow(2L, 0);
        when(inventories.availableFor(anyCollection())).thenReturn(List.of(first, second));

        Map<Long, Integer> result = service.availableFor(List.of(1L, 2L, 3L, 1L));

        assertEquals(Map.of(1L, 5, 2L, 0, 3L, 0), result);
        verify(inventories, times(1)).availableFor(anyCollection());
        verify(inventories, never()).findById(any());
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("3")));
    }

    @Test
    void availableFor_emptyOrNull_doesNotQuery() {
        assertTrue(service.availableFor(List.of()).isEmpty());
        assertTrue(service.availableFor(null).isEmpty());
        verifyNoInteractions(inventories);
    }

    @Test
    void available_isOnHandMinusReserved_andZeroWithAWarnWhenTheRowIsMissing() {
        when(inventories.findById(PRODUCT)).thenReturn(Optional.of(new Inventory(PRODUCT, 10, 4)));
        assertEquals(6, service.available(PRODUCT));

        when(inventories.findById(8L)).thenReturn(Optional.empty());
        assertEquals(0, service.available(8L));
        assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("8")));
    }

    private static AvailableRow availableRow(Long productId, int available) {
        AvailableRow row = mock(AvailableRow.class);
        when(row.getProductId()).thenReturn(productId);
        when(row.getAvailable()).thenReturn(available);
        return row;
    }
}
