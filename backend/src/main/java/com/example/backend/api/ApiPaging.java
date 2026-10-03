package com.example.backend.api;

import com.example.backend.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Builds a safe {@link Pageable} from raw client query parameters.
 * <ul>
 *   <li>{@code page}: 0-based; null or negative becomes 0.</li>
 *   <li>{@code size}: null or &lt; 1 becomes {@link #DEFAULT_SIZE}; anything above {@link #MAX_SIZE} is clamped
 *       to {@link #MAX_SIZE} (server-side, so {@code size=1000} never reaches the database).</li>
 *   <li>{@code sort}: {@code field} or {@code field,asc|desc}. The client field is looked up in a server-side
 *       whitelist and only the mapped property name is ever used; unknown fields are rejected, so client text
 *       never reaches SQL/JPQL.</li>
 * </ul>
 */
public final class ApiPaging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private ApiPaging() {
    }

    /**
     * @param allowedSortFields API field name -> entity property name (the whitelist)
     * @param defaultSort       used when {@code sort} is absent/blank; must itself use trusted property names
     * @throws BadRequestException (VALIDATION_ERROR on /api/v1) when {@code sort} is not allowed
     */
    public static Pageable of(Integer page, Integer size, String sort,
                              Map<String, String> allowedSortFields, Sort defaultSort) {
        return of(page, size, sort, allowedSortFields, defaultSort, DEFAULT_SIZE);
    }

    /** Same as above with a module-specific default size (still clamped to {@link #MAX_SIZE}). */
    public static Pageable of(Integer page, Integer size, String sort,
                              Map<String, String> allowedSortFields, Sort defaultSort, int defaultSize) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int fallback = Math.min(Math.max(defaultSize, 1), MAX_SIZE);
        int safeSize = (size == null || size < 1) ? fallback : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, parseSort(sort, allowedSortFields, defaultSort));
    }

    public static Sort parseSort(String sort, Map<String, String> allowedSortFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort == null ? Sort.unsorted() : defaultSort;
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2) {
            throw new BadRequestException("Tham số sort không hợp lệ.");
        }
        String property = allowedSortFields == null ? null : allowedSortFields.get(parts[0].trim());
        if (property == null) {
            throw new BadRequestException("Trường sắp xếp không được hỗ trợ.");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String dir = parts[1].trim();
            if (dir.equalsIgnoreCase("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!dir.equalsIgnoreCase("asc")) {
                throw new BadRequestException("Hướng sắp xếp phải là asc hoặc desc.");
            }
        }
        return Sort.by(direction, property);
    }
}
