package com.example.backend.service;

import com.example.backend.dto.ProductReviewRequest;
import com.example.backend.dto.ProductReviewResponse;
import com.example.backend.dto.ProductReviewsResponse;
import com.example.backend.entity.Product;
import com.example.backend.entity.ProductReview;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ProductReviewRepository;
import com.example.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductReviewService {

    static final String PURCHASE_REQUIRED = "Chỉ có thể đánh giá sản phẩm trong đơn hàng đã giao của bạn.";
    static final String ALREADY_REVIEWED = "Bạn đã đánh giá sản phẩm này.";

    @Autowired private ProductReviewRepository reviewRepository;
    @Autowired private ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductReviewsResponse list(Long productId, Long userId) {
        List<ProductReviewResponse> reviews = reviewRepository.findResponsesByProductId(productId);
        boolean alreadyReviewed = userId != null && reviewRepository.existsByUserIdAndProductId(userId, productId);
        boolean canReview = userId != null && !alreadyReviewed
                && reviewRepository.hasDeliveredOrderContaining(userId, productId);
        return new ProductReviewsResponse(
                reviewRepository.averageRatingByProductId(productId),
                reviewRepository.countByProductId(productId),
                reviews, canReview, alreadyReviewed);
    }

    @Transactional
    public ProductReviewResponse create(Long userId, Long productId, ProductReviewRequest request) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStatus() == Product.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm."));
        if (!reviewRepository.hasDeliveredOrderContaining(userId, productId)) {
            throw new BusinessRuleViolationException(PURCHASE_REQUIRED);
        }
        if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new ConflictException(ALREADY_REVIEWED);
        }
        ProductReview review = new ProductReview();
        review.setUserId(userId);
        review.setProductId(product.getId());
        review.setRating(request.rating());
        review.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        try {
            ProductReview saved = reviewRepository.saveAndFlush(review);
            return reviewRepository.findResponsesByProductId(productId).stream()
                    .filter(r -> r.id().equals(saved.getId())).findFirst().orElseThrow();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ALREADY_REVIEWED);
        }
    }
}
