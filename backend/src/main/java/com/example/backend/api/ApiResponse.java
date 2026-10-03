package com.example.backend.api;

/**
 * Success envelope: {@code {"data": ..., "meta": ...}}.
 * {@code meta} is {@code null} for single resources and a {@link PageMeta} for paged lists.
 */
public record ApiResponse<T>(T data, PageMeta meta) {

    /** Single resource (or non-paged value): {@code {"data": value, "meta": null}}. */
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, PageMeta meta) {
        return new ApiResponse<>(data, meta);
    }
}
