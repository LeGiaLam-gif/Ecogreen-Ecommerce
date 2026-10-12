package com.example.backend.service.inventory;

import com.example.backend.api.ApiPaging;
import com.example.backend.entity.InventoryTransaction;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.InventoryRepository;
import com.example.backend.repository.InventoryRepository.InventoryRow;
import com.example.backend.repository.InventoryTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

/**
 * Read side and the admin adjust of the inventory (B03). Stock itself is only ever changed by {@link InventoryService};
 * this class validates the query parameters, runs the read-only queries and returns the row after an adjust.
 */
@Service
public class InventoryAdminService {

    /** Same boundary the admin dashboard already uses for "low stock". */
    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;
    static final int MAX_THRESHOLD = 1_000_000;
    static final int MAX_KEYWORD_LENGTH = 100;
    /** Escape character of the LIKE pattern; the query declares {@code escape '!'}. */
    private static final char LIKE_ESCAPE = '!';

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryService inventoryService;

    public InventoryAdminService(InventoryRepository inventoryRepository,
                                 InventoryTransactionRepository transactionRepository,
                                 InventoryService inventoryService) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.inventoryService = inventoryService;
    }

    /** Page size is clamped by {@link ApiPaging} (default 20, at most 100); the order is fixed in the query. */
    public static Pageable pageable(Integer page, Integer size) {
        Pageable p = ApiPaging.of(page, size, null, Map.of(), Sort.unsorted());
        return PageRequest.of(p.getPageNumber(), p.getPageSize());
    }

    /** Ledger order: newest first, matching idx_inventory_tx_product_created. */
    public static Pageable ledgerPageable(Integer page, Integer size) {
        Pageable p = ApiPaging.of(page, size, null, Map.of(), Sort.unsorted());
        return PageRequest.of(p.getPageNumber(), p.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Transactional(readOnly = true)
    public Page<InventoryRow> list(String keyword, boolean lowStock, Integer threshold, Pageable pageable) {
        int limit = threshold == null ? DEFAULT_LOW_STOCK_THRESHOLD : threshold;
        if (limit < 0 || limit > MAX_THRESHOLD) {
            throw new BadRequestException("Ngưỡng tồn kho không hợp lệ.");
        }
        return inventoryRepository.search(likePattern(keyword), lowStock, limit, pageable);
    }

    @Transactional(readOnly = true)
    public InventoryRow get(Long productId) {
        return inventoryRepository.findRow(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tồn kho của sản phẩm."));
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransaction> ledger(Long productId, Pageable pageable) {
        if (!inventoryRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy tồn kho của sản phẩm.");
        }
        return transactionRepository.findByProductId(productId, pageable);
    }

    /** Adjusts on-hand through {@link InventoryService#adjust} (lock, rules, ledger row with the actor) and returns the new row. */
    @Transactional
    public InventoryRow adjust(Long productId, int quantityDelta, String reason, Long actorUserId) {
        inventoryService.adjust(productId, quantityDelta, reason, actorUserId);
        return get(productId);
    }

    /** Lower-cased LIKE pattern with %, _ and the escape character escaped; "%" when there is no keyword. */
    static String likePattern(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return "%";
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BadRequestException("Từ khóa tối đa " + MAX_KEYWORD_LENGTH + " ký tự.");
        }
        StringBuilder sb = new StringBuilder("%");
        for (char c : trimmed.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
                sb.append(LIKE_ESCAPE);
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }
}
