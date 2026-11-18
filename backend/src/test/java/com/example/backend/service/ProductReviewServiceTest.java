package com.example.backend.service;

import com.example.backend.dto.ProductReviewRequest;
import com.example.backend.dto.ProductReviewResponse;
import com.example.backend.entity.Product;
import com.example.backend.entity.ProductReview;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.ProductReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTest {

    private static final long CUSTOMER = 3L;
    private static final long PRODUCT = 9L;

    @Mock private ProductReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private ProductReviewService service;

    @Test
    void createAllowsOneReviewForAnOwnedDeliveredPurchase() {
        Product product = new Product();
        product.setId(PRODUCT);
        product.setStatus(Product.Status.ACTIVE);
        when(productRepository.findById(PRODUCT)).thenReturn(Optional.of(product));
        when(reviewRepository.hasDeliveredOrderContaining(CUSTOMER, PRODUCT)).thenReturn(true);
        when(reviewRepository.existsByUserIdAndProductId(CUSTOMER, PRODUCT)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(ProductReview.class))).thenAnswer(invocation -> {
            ProductReview saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        ProductReviewResponse response = new ProductReviewResponse(11L, "alice", 5, "Excellent", null);
        when(reviewRepository.findResponsesByProductId(PRODUCT)).thenReturn(List.of(response));

        ProductReviewResponse created = service.create(CUSTOMER, PRODUCT, new ProductReviewRequest(5, " Excellent "));

        assertEquals(response, created);
        verify(reviewRepository).saveAndFlush(any(ProductReview.class));
    }

    @Test
    void createRejectsAProductWithoutTheCallersDeliveredPurchase() {
        Product product = new Product();
        product.setId(PRODUCT);
        product.setStatus(Product.Status.ACTIVE);
        when(productRepository.findById(PRODUCT)).thenReturn(Optional.of(product));
        when(reviewRepository.hasDeliveredOrderContaining(CUSTOMER, PRODUCT)).thenReturn(false);

        assertThrows(BusinessRuleViolationException.class,
                () -> service.create(CUSTOMER, PRODUCT, new ProductReviewRequest(4, null)));

        verify(reviewRepository, never()).saveAndFlush(any(ProductReview.class));
    }

    @Test
    void createRejectsASecondReviewForTheSameUserAndProduct() {
        Product product = new Product();
        product.setId(PRODUCT);
        product.setStatus(Product.Status.ACTIVE);
        when(productRepository.findById(PRODUCT)).thenReturn(Optional.of(product));
        when(reviewRepository.hasDeliveredOrderContaining(CUSTOMER, PRODUCT)).thenReturn(true);
        when(reviewRepository.existsByUserIdAndProductId(CUSTOMER, PRODUCT)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.create(CUSTOMER, PRODUCT, new ProductReviewRequest(4, null)));

        verify(reviewRepository, never()).saveAndFlush(any(ProductReview.class));
    }
}
