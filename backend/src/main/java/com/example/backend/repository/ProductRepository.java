package com.example.backend.repository;

import com.example.backend.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    List<Product> findByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = { "category" })
    List<Product> findByStatus(Product.Status status);

    @Override
    @EntityGraph(attributePaths = { "category" })
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = { "category" })
    Optional<Product> findById(Long id);

    /**
     * Catalogue search: the to-one category is fetched in the same statement (no
     * N+1); images are loaded separately.
     */
    @Override
    @EntityGraph(attributePaths = { "category" })
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = { "category" })
    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    boolean existsByCategoryId(Long categoryId);

    List<Product> findByNameContainingIgnoreCase(String name);

    /**
     * TEMPORARY-COMPAT(B05): atomic stock decrement for the legacy inventory
     * adapter (removed by B03). One statement checks
     * and decrements, so two simultaneous orders for the last unit cannot both
     * succeed. Returns the number of rows changed:
     * 1 = reserved, 0 = not enough stock (or no such product). It bypasses the
     * persistence context, so callers must not
     * save() a Product instance they loaded earlier in the same transaction (that
     * would write the stale stock back).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity "
            + "WHERE p.id = :id AND p.stockQuantity >= :quantity")
    int decreaseStockIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * TEMPORARY-COMPAT(B05): atomic stock increment (release / restock) for the
     * legacy adapter. Returns rows changed.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity WHERE p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
