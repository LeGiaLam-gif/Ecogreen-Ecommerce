package com.example.backend.repository;

import com.example.backend.entity.Inventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** One row of {@link #availableFor}: the available quantity is computed in SQL, never stored. */
    interface AvailableRow {
        Long getProductId();

        Integer getAvailable();
    }

    /** One admin list/detail row: the inventory row plus the product's name and SKU. Admin only (shows reserved). */
    interface InventoryRow {
        Long getProductId();

        String getProductName();

        String getSku();

        Integer getQuantityOnHand();

        Integer getReservedQuantity();

        LocalDateTime getUpdatedAt();
    }

    /**
     * Shared by the list and its count. {@code :pattern} is a lower-cased LIKE pattern whose %, _ and ! are escaped with
     * {@code !} (pass {@code "%"} for "no keyword"), so client text is always a parameter, never part of the query.
     * {@code :lowOnly = false} disables the low-stock filter; otherwise available (on hand - reserved) must be at or
     * below {@code :threshold}.
     */
    String SEARCH_FROM = "from Inventory i join Product p on p.id = i.productId "
            + "where (lower(p.name) like :pattern escape '!' or lower(coalesce(p.sku, '')) like :pattern escape '!') "
            + "and (:lowOnly = false or (i.quantityOnHand - i.reservedQuantity) <= :threshold)";

    String ROW_SELECT = "select i.productId as productId, p.name as productName, p.sku as sku, "
            + "i.quantityOnHand as quantityOnHand, i.reservedQuantity as reservedQuantity, i.updatedAt as updatedAt ";

    /**
     * Admin list, ordered by available ascending (what needs attention first), then name, then id. The order is fixed
     * in the query on purpose: there is no client-controlled sort, so nothing from the client reaches ORDER BY.
     */
    @Query(value = ROW_SELECT + SEARCH_FROM
            + " order by (i.quantityOnHand - i.reservedQuantity) asc, p.name asc, i.productId asc",
            countQuery = "select count(i) " + SEARCH_FROM)
    Page<InventoryRow> search(@Param("pattern") String pattern, @Param("lowOnly") boolean lowOnly,
                              @Param("threshold") int threshold, Pageable pageable);

    @Query(ROW_SELECT + "from Inventory i join Product p on p.id = i.productId where i.productId = :productId")
    Optional<InventoryRow> findRow(@Param("productId") Long productId);

    /**
     * Loads the row with {@code SELECT ... FOR UPDATE}. Every stock change takes this lock first and checks its rules on
     * the LOCKED values. The hint asks for a bounded wait (3 s) instead of waiting forever; whether the PostgreSQL dialect
     * applies a positive timeout is NOT verified here (PostgreSQL's own deadlock detection still applies either way).
     * Callers that lock several products must do so in ascending productId order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select i from Inventory i where i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    /** Backs {@code availableFor(ids)}: ONE query for any number of products (product list, cart). */
    @Query("select i.productId as productId, (i.quantityOnHand - i.reservedQuantity) as available "
            + "from Inventory i where i.productId in :ids")
    List<AvailableRow> availableFor(@Param("ids") Collection<Long> ids);
}
