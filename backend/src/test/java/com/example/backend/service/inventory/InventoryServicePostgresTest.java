package com.example.backend.service.inventory;

import com.example.backend.dto.ProductCreateRequest;
import com.example.backend.dto.ProductUpdateRequest;
import com.example.backend.entity.Category;
import com.example.backend.entity.Inventory;
import com.example.backend.entity.InventoryTransaction;
import com.example.backend.entity.InventoryTransaction.ChangeType;
import com.example.backend.entity.Product;
import com.example.backend.repository.CategoryRepository;
import com.example.backend.repository.InventoryRepository;
import com.example.backend.repository.InventoryRepository.InventoryRow;
import com.example.backend.repository.InventoryTransactionRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B03 against a REAL PostgreSQL with the REAL Spring context: the real InventoryService (row lock, ledger), ProductService
 * create/update, and the admin HQL queries (the part no mock can check). Needs ECOGREEN_TEST_PG_URL (optional
 * ECOGREEN_TEST_PG_USER / ECOGREEN_TEST_PG_PASSWORD); otherwise skipped = NOT RUN.
 * WARNING: it boots the application against that database with ddl-auto=update and inserts/removes its own rows (names
 * prefixed with a random tag). Point it only at a scratch database. If V30/V31 were not applied, Hibernate creates the
 * tables without foreign keys / CHECKs / the unique partial index, so only the rules of the SERVICE are exercised then.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test-jwt.properties")
@EnabledIfEnvironmentVariable(named = "ECOGREEN_TEST_PG_URL", matches = ".+")
class InventoryServicePostgresTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ECOGREEN_TEST_PG_URL");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ECOGREEN_TEST_PG_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired private ProductService productService;
    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryAdminService adminService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryTransactionRepository ledgerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final List<Long> created = new ArrayList<>();
    private Category category;

    @AfterEach
    void cleanUp() {
        for (Long id : created) {
            ledgerRepository.deleteAll(ledgerRepository.findByProductId(id, Pageable.unpaged()).getContent());
            inventoryRepository.deleteById(id);
            productRepository.deleteById(id);
        }
        if (category != null) categoryRepository.deleteById(category.getId());
    }

    private Long newProduct(String name, int stock) {
        if (category == null) {
            category = new Category();
            category.setName(tag + "-category");
            category.setSlug(tag + "-category");
            category = categoryRepository.save(category);
        }
        Product p = productService.create(new ProductCreateRequest(tag + " " + name, "mô tả", new BigDecimal("10000"), null,
                stock, null, category.getId(), null, null, Product.Status.ACTIVE), null);
        created.add(p.getId());
        return p.getId();
    }

    private List<InventoryTransaction> ledgerOf(Long productId) {
        return ledgerRepository.findByProductId(productId, Pageable.unpaged()).getContent();
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    /** The ledger reconciles through the *_after columns of the LAST row, never by summing quantities. */
    private void assertLedgerMatchesRow(Long productId) {
        Inventory row = inventoryRepository.findById(productId).orElseThrow();
        List<InventoryTransaction> rows = new ArrayList<>(ledgerOf(productId));
        rows.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        InventoryTransaction last = rows.get(rows.size() - 1);
        assertEquals(row.getQuantityOnHand(), last.getOnHandAfter());
        assertEquals(row.getReservedQuantity(), last.getReservedAfter());
    }

    // ------------------------------------------------------------------ product create / update

    @Test
    void create_writesTheInventoryRow_andAnInitialStockLedgerRow() {
        Long id = newProduct("initial", 7);

        Inventory row = inventoryRepository.findById(id).orElseThrow();
        assertEquals(7, row.getQuantityOnHand());
        assertEquals(0, row.getReservedQuantity());
        List<InventoryTransaction> ledger = ledgerOf(id);
        assertEquals(1, ledger.size());
        assertEquals(ChangeType.RESTOCK, ledger.get(0).getChangeType());
        assertEquals("Initial stock", ledger.get(0).getReason());
        assertLedgerMatchesRow(id);
    }

    @Test
    void update_withTheSameAvailable_writesNothing_andWithANewOne_writesOneAdjustRow() {
        Long id = newProduct("form", 10);
        inTransaction(() -> {
            inventoryService.reserve(id, 3, "ORDER", 9001L);
            return null;
        });
        ProductUpdateRequest sameAvailable = new ProductUpdateRequest(null, null, null, null, null, 7, null, null, null, null, null);
        ProductUpdateRequest moreStock = new ProductUpdateRequest(null, null, null, null, null, 12, null, null, null, null, null);

        productService.update(id, sameAvailable, null); // form loaded available = 7: no change, no ledger row
        assertEquals(2, ledgerOf(id).size()); // initial stock + the reservation

        productService.update(id, moreStock, null);
        List<InventoryTransaction> ledger = ledgerOf(id);
        assertEquals(3, ledger.size());
        assertEquals(1, ledger.stream().filter(t -> t.getChangeType() == ChangeType.ADJUST
                && "Admin product form".equals(t.getReason()) && t.getQuantity() == 5).count());
        Inventory row = inventoryRepository.findById(id).orElseThrow();
        assertEquals(15, row.getQuantityOnHand());
        assertEquals(3, row.getReservedQuantity());
        assertLedgerMatchesRow(id);
    }

    // ------------------------------------------------------------------ rules through the real service

    @Test
    void reserve_replaySameReference_countsOnce() {
        Long id = newProduct("replay", 5);

        inTransaction(() -> {
            inventoryService.reserve(id, 2, "ORDER", 77L);
            inventoryService.reserve(id, 2, "ORDER", 77L); // webhook retry
            return null;
        });

        assertEquals(2, inventoryRepository.findById(id).orElseThrow().getReservedQuantity());
        assertEquals(1, ledgerOf(id).stream().filter(t -> t.getChangeType() == ChangeType.RESERVE).count());
    }

    @Test
    void reserve_moreThanAvailable_throws_andLeavesTheRowUntouched() {
        Long id = newProduct("short", 2);

        InsufficientStockException ex = org.junit.jupiter.api.Assertions.assertThrows(InsufficientStockException.class,
                () -> inTransaction(() -> {
                    inventoryService.reserve(id, 3, "ORDER", 78L);
                    return null;
                }));

        assertTrue(ex.getMessage().contains(tag));
        assertEquals(0, inventoryRepository.findById(id).orElseThrow().getReservedQuantity());
    }

    private static final int CONTENDERS = 8;

    @RepeatedTest(5)
    void reserve_concurrent_lastUnit_exactlyOneSucceeds() throws Exception {
        Long id = newProduct("last-unit", 1);
        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        CyclicBarrier start = new CyclicBarrier(CONTENDERS);
        int winners = 0;
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < CONTENDERS; i++) {
                final long orderId = 5000L + i;
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        inTransaction(() -> {
                            inventoryService.reserve(id, 1, "ORDER", orderId);
                            return null;
                        });
                        return true;
                    } catch (InsufficientStockException e) {
                        return false;
                    }
                }));
            }
            for (Future<Boolean> f : results) {
                if (f.get(60, TimeUnit.SECONDS)) winners++;
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, winners, "exactly one concurrent reservation may take the last unit");
        Inventory row = inventoryRepository.findById(id).orElseThrow();
        assertEquals(1, row.getReservedQuantity());
        assertEquals(0, row.available(), "never oversold");
        assertEquals(1, ledgerOf(id).stream().filter(t -> t.getChangeType() == ChangeType.RESERVE).count());
        assertLedgerMatchesRow(id);
    }

    @Test
    void reserve_twoOrdersOppositeItemOrder_noDeadlock_whenLockedInAscendingProductIdOrder() throws Exception {
        Long a = newProduct("pair-a", 10);
        Long b = newProduct("pair-b", 10);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(pool.submit(() -> placeOrder(List.of(a, b), 601L)));
            futures.add(pool.submit(() -> placeOrder(List.of(b, a), 602L)));
            for (Future<?> f : futures) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue(failures.isEmpty(), "ascending lock order must not deadlock: " + failures);
        assertEquals(2, inventoryRepository.findById(a).orElseThrow().getReservedQuantity());
        assertEquals(2, inventoryRepository.findById(b).orElseThrow().getReservedQuantity());
    }

    /** One order = several items in ONE transaction, items sorted ascending like OrderService does. */
    private Void placeOrder(List<Long> productIds, long orderId) throws InterruptedException {
        List<Long> sorted = new ArrayList<>(productIds);
        Collections.sort(sorted);
        inTransaction(() -> {
            for (Long productId : sorted) {
                inventoryService.reserve(productId, 1, "ORDER", orderId);
                try {
                    Thread.sleep(200); // widen the overlap window
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        });
        return null;
    }

    // ------------------------------------------------------------------ admin queries (the HQL)

    @Test
    void adminList_lowStock_filtersByAvailable_ordersLowestFirst_andKeywordIsLiteral() {
        Long empty = newProduct("empty", 0);
        Long low = newProduct("low", 3);
        newProduct("plenty", 50);
        Long reservedHeavy = newProduct("held", 8);
        inTransaction(() -> {
            inventoryService.reserve(reservedHeavy, 6, "ORDER", 91L); // available 2
            return null;
        });

        Page<InventoryRow> lowStock = adminService.list(tag, true, 5, InventoryAdminService.pageable(0, 20));

        assertEquals(List.of(empty, reservedHeavy, low), lowStock.getContent().stream().map(InventoryRow::getProductId).toList());
        assertEquals(3, lowStock.getTotalElements());
        // threshold alone (lowStock=false) is ignored: everything of this run is listed
        assertEquals(4, adminService.list(tag, false, 5, InventoryAdminService.pageable(0, 20)).getTotalElements());
        // a keyword with LIKE wildcards matches literally, so "%" matches nothing here
        assertEquals(0, adminService.list("%", false, null, InventoryAdminService.pageable(0, 20)).getTotalElements());
        InventoryRow detail = adminService.get(reservedHeavy);
        assertEquals(8, detail.getQuantityOnHand());
        assertEquals(6, detail.getReservedQuantity());
        assertEquals(tag + " held", detail.getProductName());
    }

    @Test
    void adminAdjust_belowReserved_isRejected_andAboveWritesALedgerRowWithTheActorAndReason() {
        Long id = newProduct("adjust", 10);
        inTransaction(() -> {
            inventoryService.reserve(id, 6, "ORDER", 92L);
            return null;
        });

        org.junit.jupiter.api.Assertions.assertThrows(com.example.backend.exception.BusinessRuleViolationException.class,
                () -> adminService.adjust(id, -5, "damaged", null)); // 10 - 5 = 5 < reserved 6
        InventoryRow after = adminService.adjust(id, -4, "damaged", null); // 6 = reserved: allowed

        assertEquals(6, after.getQuantityOnHand());
        List<InventoryTransaction> adjusts = ledgerOf(id).stream().filter(t -> t.getChangeType() == ChangeType.ADJUST).toList();
        assertEquals(1, adjusts.size());
        assertEquals(-4, adjusts.get(0).getQuantity());
        assertEquals("damaged", adjusts.get(0).getReason());
        assertLedgerMatchesRow(id);
    }
}
