package com.example.backend.dto;

import java.time.LocalDateTime;

public record ProductReviewResponse(Long id, String username, int rating, String comment,
                                    LocalDateTime createdAt) {}
