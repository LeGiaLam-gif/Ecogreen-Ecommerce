package com.example.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProductReviewRequest(
        @Min(1) @Max(5) int rating,
        @Size(max = 2000) String comment
) {}
