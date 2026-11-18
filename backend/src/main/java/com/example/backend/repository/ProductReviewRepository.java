package com.example.backend.repository;

import com.example.backend.dto.ProductReviewResponse;
import com.example.backend.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    @Query("SELECT new com.example.backend.dto.ProductReviewResponse(r.id, u.username, r.rating, r.comment, r.createdAt) "
            + "FROM ProductReview r JOIN User u ON u.id = r.userId "
            + "WHERE r.productId = :productId ORDER BY r.createdAt DESC, r.id DESC")
    List<ProductReviewResponse> findResponsesByProductId(@Param("productId") Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM ProductReview r WHERE r.productId = :productId")
    Double averageRatingByProductId(@Param("productId") Long productId);

    long countByProductId(Long productId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM orders o JOIN order_items oi ON oi.order_id = o.id "
            + "WHERE o.user_id = :userId AND o.status = 'DELIVERED' AND oi.product_id = :productId)",
            nativeQuery = true)
    boolean hasDeliveredOrderContaining(@Param("userId") Long userId, @Param("productId") Long productId);
}
