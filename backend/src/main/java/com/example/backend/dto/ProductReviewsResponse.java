package com.example.backend.dto;

import java.util.List;

public record ProductReviewsResponse(double averageRating, long reviewCount, List<ProductReviewResponse> reviews,
                                     boolean canReview, boolean alreadyReviewed) {}
