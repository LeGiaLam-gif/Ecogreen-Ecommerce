package com.example.backend.repository;

import com.example.backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** One IN query for a whole page of products. */
    List<ProductImage> findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(Collection<Long> productIds);

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    boolean existsByProductId(Long productId);
}
