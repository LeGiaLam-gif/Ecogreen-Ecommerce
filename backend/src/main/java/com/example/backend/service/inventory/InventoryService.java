package com.example.backend.service.inventory;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * B03: the reservation model behind {@link InventoryGateway} (on-hand / reserved, never a stored "available") and the only
 * place that changes stock.
 *
 * <p><b>Rules every operation follows.</b> (1) It takes the product's inventory row with a pessimistic lock FIRST and checks
 * its rules on the LOCKED values, never on values read earlier. (2) It writes exactly one ledger row with the after-values,
 * in the same transaction. (3) Replaying the same {@code (product, change type, refType, refId)} for RESERVE / RELEASE /
 * COMMIT / RESTOCK is a logged no-op (the unique partial index of V31 is the backstop). (4) No remote call happens inside
 * the locked section.
 *
 * <p><b>Lock ordering.</b> This service is per product. Whoever touches SEVERAL products in one transaction (an order with
 * several items) must call it in ascending productId order, otherwise two orders listing the same items in opposite order
 * can deadlock. OrderService already sorts its items; B04 and B07 must do the same.
 *
 * <p>The mutating gateway methods require the caller's transaction ({@code MANDATORY}) so an order and its stock movements
 * commit or roll back together. {@code adjust}, {@code setAvailable} and {@code initialize} start their own when called alone.
 *
 * <p>This is the ONLY implementation of {@link InventoryGateway} (B05's legacy adapter over products.stock_quantity is gone).
 */
@Service
public class InventoryService implements InventoryGateway {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    static final int MAX_REASON_LENGTH = 255;
    static final int MAX_REFERENCE_TYPE_LENGTH = 30;
    static final String INITIAL_STOCK_REASON = "Initial stock";

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryTransactionRepository transactionRepository,
                            ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.productRepository = productRepository;
    }

    // ------------------------------------------------------------------ gateway operations

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long productId, int quantity, String refType, Long refId) {
        requirePositive(quantity);
        requireReference(refType, refId);
        Optional<Inventory> locked = inventoryRepository.findByProductIdForUpdate(productId);
        if (locked.isEmpty()) {
            // Fail closed: a product without an inventory row has nothing to sell.
            log.warn("reserve: product {} has no inventory row (V30 not applied or row missing); treating as 0 available", productId);
            throw new InsufficientStockException(productId, quantity, productName(productId));
        }
        Inventory inventory = locked.get();
        if (isReplay(productId, ChangeType.RESERVE, refType, refId)) {
            return;
        }
        // The rule is checked AFTER the lock, on the locked row.
        if (inventory.available() < quantity) {
            throw new InsufficientStockException(productId, quantity, productName(productId));
        }
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        writeLedger(inventory, ChangeType.RESERVE, quantity, refType, refId, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long productId, int quantity, String refType, Long refId) {
        requirePositive(quantity);
        requireReference(refType, refId);
        Inventory inventory = lockExisting(productId, "release");
        if (isReplay(productId, ChangeType.RELEASE, refType, refId)) {
            return;
        }
        requireReservedAtLeast(inventory, quantity, "release");
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        writeLedger(inventory, ChangeType.RELEASE, quantity, refType, refId, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(Long productId, int quantity, String refType, Long refId) {
        requirePositive(quantity);
        requireReference(refType, refId);
        Inventory inventory = lockExisting(productId, "commit");
        if (isReplay(productId, ChangeType.COMMIT, refType, refId)) {
            return;
        }
        requireReservedAtLeast(inventory, quantity, "commit");
        inventory.setQuantityOnHand(inventory.getQuantityOnHand() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        writeLedger(inventory, ChangeType.COMMIT, quantity, refType, refId, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(Long productId, int quantity, String refType, Long refId) {
        requirePositive(quantity);
        requireReference(refType, refId);
        Inventory inventory = lockExisting(productId, "restock");
        if (isReplay(productId, ChangeType.RESTOCK, refType, refId)) {
            return;
        }
        inventory.setQuantityOnHand(addOnHand(inventory.getQuantityOnHand(), quantity));
        writeLedger(inventory, ChangeType.RESTOCK, quantity, refType, refId, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public int available(Long productId) {
        Optional<Inventory> row = inventoryRepository.findById(productId);
        if (row.isEmpty()) {
            log.warn("available: product {} has no inventory row; returning 0", productId);
            return 0;
        }
        return row.get().available();
    }

    /**
     * Available quantity for many products with ONE query. Every requested id is present in the result; a product without
     * an inventory row maps to 0 (and is logged).
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> availableFor(Collection<Long> productIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : productIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        for (AvailableRow row : inventoryRepository.availableFor(ids)) {
            result.put(row.getProductId(), row.getAvailable());
        }
        List<Long> missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            log.warn("availableFor: product id(s) {} have no inventory row; returning 0", missing);
            missing.forEach(id -> result.put(id, 0));
        }
        return result;
    }

    // ------------------------------------------------------------------ admin / product operations

    /**
     * Changes on-hand by {@code delta} (positive or negative). A reason is mandatory; the result may not drop below the
     * reserved quantity. Returns the row after the change.
     */
    @Transactional
    public Inventory adjust(Long productId, int delta, String reason, Long actorUserId) {
        String cleanReason = requireReason(reason);
        if (delta == 0) {
            throw new BadRequestException("Số lượng điều chỉnh phải khác 0.");
        }
        Inventory inventory = lockExisting(productId, "adjust");
        applyDelta(inventory, delta, cleanReason, actorUserId);
        return inventory;
    }

    /**
     * Sets the AVAILABLE quantity to {@code targetAvailable}: the comparison and the change both use the LOCKED row, so
     * {@code delta = target - available} is exact even if customers reserved stock a moment ago. A delta of 0 writes nothing
     * (no ledger row). The result is always {@code >= reserved} because the target is not negative.
     *
     * @return the delta that was applied (0 when nothing changed)
     */
    @Transactional
    public int setAvailable(Long productId, int targetAvailable, String reason, Long actorUserId) {
        String cleanReason = requireReason(reason);
        if (targetAvailable < 0) {
            throw new BadRequestException("Số lượng tồn kho không được âm.");
        }
        Inventory inventory = lockExisting(productId, "setAvailable");
        int delta = targetAvailable - inventory.available();
        if (delta == 0) {
            return 0;
        }
        applyDelta(inventory, delta, cleanReason, actorUserId);
        return delta;
    }

    /**
     * Creates the inventory row of a NEW product and the ledger row that explains its starting stock: RESTOCK
     * "Initial stock" when the stock is positive, otherwise an ADJUST of 0 (a RESTOCK of 0 is forbidden by the ledger CHECK),
     * so every inventory row has a first ledger row.
     */
    @Transactional
    public void initialize(Long productId, int initialStock, Long actorUserId) {
        if (initialStock < 0) {
            throw new BadRequestException("Số lượng tồn kho không được âm.");
        }
        if (inventoryRepository.existsById(productId)) {
            log.warn("initialize: product {} already has an inventory row; nothing created", productId);
            return;
        }
        Inventory inventory = new Inventory(productId, initialStock, 0);
        ChangeType type = initialStock > 0 ? ChangeType.RESTOCK : ChangeType.ADJUST;
        inventoryRepository.save(inventory);
        transactionRepository.save(new InventoryTransaction(productId, type, initialStock, initialStock, 0,
                null, null, INITIAL_STOCK_REASON, actorUserId));
    }

    // ------------------------------------------------------------------ internals

    private void applyDelta(Inventory inventory, int delta, String reason, Long actorUserId) {
        int newOnHand;
        try {
            newOnHand = Math.addExact(inventory.getQuantityOnHand(), delta);
        } catch (ArithmeticException e) {
            throw new BadRequestException("Số lượng điều chỉnh quá lớn.");
        }
        if (newOnHand < inventory.getReservedQuantity()) {
            throw new BusinessRuleViolationException(
                    "Tồn kho sau điều chỉnh không được thấp hơn số lượng đang được giữ cho các đơn hàng chưa thanh toán.");
        }
        inventory.setQuantityOnHand(newOnHand);
        writeLedger(inventory, ChangeType.ADJUST, delta, null, null, reason, actorUserId);
    }

    private Inventory lockExisting(Long productId, String operation) {
        return inventoryRepository.findByProductIdForUpdate(productId).orElseThrow(() -> {
            log.warn("{}: product {} has no inventory row (V30 not applied or row missing)", operation, productId);
            return new ResourceNotFoundException("Sản phẩm không còn tồn tại.");
        });
    }

    /** A replay is a no-op, logged at INFO. Runs while the row is locked, so two replays cannot interleave. */
    private boolean isReplay(Long productId, ChangeType type, String refType, Long refId) {
        if (transactionRepository.existsByProductIdAndChangeTypeAndReferenceTypeAndReferenceId(productId, type, refType, refId)) {
            log.info("{} for product {} and reference {}#{} was already applied; ignoring the replay", type, productId, refType, refId);
            return true;
        }
        return false;
    }

    private void writeLedger(Inventory inventory, ChangeType type, int quantity, String refType, Long refId,
                             String reason, Long actorUserId) {
        inventoryRepository.save(inventory);
        transactionRepository.save(new InventoryTransaction(inventory.getProductId(), type, quantity,
                inventory.getQuantityOnHand(), inventory.getReservedQuantity(), refType, refId, reason, actorUserId));
    }

    private String productName(Long productId) {
        // Failure path only: the public catalogue name for the customer-facing message.
        return productRepository.findById(productId).map(Product::getName).orElse(null);
    }

    private static int addOnHand(int onHand, int quantity) {
        try {
            return Math.addExact(onHand, quantity);
        } catch (ArithmeticException e) {
            throw new BadRequestException("Số lượng quá lớn.");
        }
    }

    private static void requireReservedAtLeast(Inventory inventory, int quantity, String operation) {
        if (inventory.getReservedQuantity() < quantity) {
            // Data/logic error, not a customer mistake: fail the whole transaction rather than invent a number.
            log.error("{} of {} unit(s) for product {} exceeds the reserved quantity {}", operation, quantity,
                    inventory.getProductId(), inventory.getReservedQuantity());
            throw new IllegalStateException("Inventory " + operation + " exceeds the reserved quantity for product "
                    + inventory.getProductId());
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Số lượng phải lớn hơn 0.");
        }
    }

    private static void requireReference(String refType, Long refId) {
        // The idempotency rule needs both; a movement without a reference could be applied twice.
        if (refType == null || refType.isBlank() || refId == null || refType.length() > MAX_REFERENCE_TYPE_LENGTH) {
            throw new IllegalArgumentException("Inventory movements need a reference type (max "
                    + MAX_REFERENCE_TYPE_LENGTH + " characters) and a reference id.");
        }
    }

    private static String requireReason(String reason) {
        String trimmed = Objects.toString(reason, "").trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Lý do điều chỉnh là bắt buộc.");
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("Lý do điều chỉnh tối đa " + MAX_REASON_LENGTH + " ký tự.");
        }
        return trimmed;
    }
}
