package com.example.backend.api;

/** Pagination metadata. {@code page} is 0-based. */
public record PageMeta(int page, int size, long totalElements, int totalPages) {
}
