package com.example.backend.dto;

import com.example.backend.entity.Product;
import com.example.backend.exception.BadRequestException;

import java.math.BigDecimal;

/**
 * Filters of the catalogue list. Normalised on construction: a blank keyword means "no keyword filter";
 * negative prices and an inverted price range are rejected (400 VALIDATION_ERROR on /api/v1).
 * {@code status} is honoured only by the admin search; the public search always forces ACTIVE.
 */
public record ProductSearchCriteria(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                    Boolean inStock, Product.Status status) {

    public static final int MAX_KEYWORD_LENGTH = 100;

    public ProductSearchCriteria {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BadRequestException("Từ khóa tìm kiếm quá dài (tối đa " + MAX_KEYWORD_LENGTH + " ký tự).");
        }
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new BadRequestException("Khoảng giá không được âm.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Giá tối thiểu không được lớn hơn giá tối đa.");
        }
    }

    public static ProductSearchCriteria none() {
        return new ProductSearchCriteria(null, null, null, null, null, null);
    }
}
