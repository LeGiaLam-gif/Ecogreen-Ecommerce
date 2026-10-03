package com.example.backend.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Converts a Spring Data {@link Page} into the contract envelope {@code {data: [...], meta: {...}}}.
 * Reads only values the {@code Page} already holds (no extra query, no second count).
 */
public final class PageResponse {

    private PageResponse() {
    }

    public static <T> ApiResponse<List<T>> of(Page<T> page) {
        return ApiResponse.of(page.getContent(), metaOf(page));
    }

    /** Maps entities to DTOs while keeping the page metadata, e.g. {@code PageResponse.of(page, ProductDto::from)}. */
    public static <S, T> ApiResponse<List<T>> of(Page<S> page, Function<? super S, ? extends T> mapper) {
        List<T> items = page.getContent().stream().<T>map(mapper).toList();
        return ApiResponse.of(items, metaOf(page));
    }

    public static PageMeta metaOf(Page<?> page) {
        return new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
