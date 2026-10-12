package com.example.backend.repository;

import com.example.backend.dto.ProductSearchCriteria;
import com.example.backend.entity.Inventory;
import com.example.backend.entity.Product;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Catalogue filters as JPA Criteria Specifications. Every client value is bound as a parameter; nothing is
 * concatenated into JPQL/SQL. The keyword is matched literally: {@code %}, {@code _} and the escape character are
 * escaped before use in LIKE.
 */
public final class ProductSpecifications {

    public static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
    }

    /** Escapes backslash first, then % and _, so the keyword is always a literal substring. */
    public static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * @param statusFilter the only status to return; the public search passes ACTIVE, the admin search passes the
     *                     optional status filter (null = every status)
     */
    public static Specification<Product> matching(ProductSearchCriteria c, Product.Status statusFilter) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();

        if (statusFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusFilter));
        }
        if (c.keyword() != null) {
            String pattern = "%" + escapeLike(c.keyword().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> {
                Expression<String> name = cb.lower(root.<String>get("name"));
                Expression<String> description = cb.lower(root.<String>get("description"));
                return cb.or(cb.like(name, pattern, LIKE_ESCAPE), cb.like(description, pattern, LIKE_ESCAPE));
            });
        }
        if (c.categoryId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), c.categoryId()));
        }
        if (c.minPrice() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<java.math.BigDecimal>get("price"), c.minPrice()));
        }
        if (c.maxPrice() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.<java.math.BigDecimal>get("price"), c.maxPrice()));
        }
        if (Boolean.TRUE.equals(c.inStock())) {
            // B03: in stock = available (on hand minus reserved) > 0 in the inventory, not the frozen products column.
            spec = spec.and((root, query, cb) -> {
                Subquery<Integer> available = query.subquery(Integer.class);
                Root<Inventory> inventory = available.from(Inventory.class);
                available.select(cb.literal(1)).where(
                        cb.equal(inventory.get("productId"), root.get("id")),
                        cb.greaterThan(cb.diff(inventory.<Integer>get("quantityOnHand"),
                                inventory.<Integer>get("reservedQuantity")), 0));
                return cb.exists(available);
            });
        }
        return spec;
    }
}
