package com.example.backend.api;

import com.example.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiPagingTest {

    private static final Map<String, String> WHITELIST = Map.of("price", "price", "created", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void sizeAbove100_isClampedTo100() {
        assertEquals(100, ApiPaging.of(0, 1000, null, WHITELIST, DEFAULT_SORT).getPageSize());
        assertEquals(100, ApiPaging.of(0, 101, null, WHITELIST, DEFAULT_SORT).getPageSize());
    }

    @Test
    void missingOrNonPositiveSize_usesDefault20_andNegativePageBecomesZero() {
        Pageable p = ApiPaging.of(-5, null, null, WHITELIST, DEFAULT_SORT);
        assertEquals(0, p.getPageNumber());
        assertEquals(20, p.getPageSize());
        assertEquals(20, ApiPaging.of(0, 0, null, WHITELIST, DEFAULT_SORT).getPageSize());
    }

    @Test
    void moduleDefaultSize_isUsedButStillClamped() {
        assertEquals(12, ApiPaging.of(0, null, null, WHITELIST, DEFAULT_SORT, 12).getPageSize());
        assertEquals(100, ApiPaging.of(0, null, null, WHITELIST, DEFAULT_SORT, 500).getPageSize());
    }

    @Test
    void sort_usesOnlyMappedPropertyNames() {
        Sort sort = ApiPaging.of(0, 10, "created,asc", WHITELIST, DEFAULT_SORT).getSort();
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("createdAt").getDirection()); // API "created" -> property
        assertNull(sort.getOrderFor("created"));
        assertEquals(Sort.Direction.DESC, ApiPaging.parseSort("price,DESC", WHITELIST, DEFAULT_SORT)
                .getOrderFor("price").getDirection());
        assertEquals(Sort.Direction.ASC, ApiPaging.parseSort("price", WHITELIST, DEFAULT_SORT)
                .getOrderFor("price").getDirection());
    }

    @Test
    void absentSort_usesDefaultSort() {
        assertEquals(DEFAULT_SORT, ApiPaging.parseSort(null, WHITELIST, DEFAULT_SORT));
        assertEquals(DEFAULT_SORT, ApiPaging.parseSort("  ", WHITELIST, DEFAULT_SORT));
    }

    @Test
    void nonWhitelistedOrMalformedSort_isRejected() {
        assertThrows(BadRequestException.class, () -> ApiPaging.parseSort("password", WHITELIST, DEFAULT_SORT));
        assertThrows(BadRequestException.class, () -> ApiPaging.parseSort("price;drop table x", WHITELIST, DEFAULT_SORT));
        assertThrows(BadRequestException.class, () -> ApiPaging.parseSort("price,asc,name", WHITELIST, DEFAULT_SORT));
        assertThrows(BadRequestException.class, () -> ApiPaging.parseSort("price,up", WHITELIST, DEFAULT_SORT));
        assertThrows(BadRequestException.class, () -> ApiPaging.parseSort("price", null, DEFAULT_SORT));
    }

    @Test
    void pageResponse_convertsPageWithoutExtraCount() {
        Pageable pageable = PageRequest.of(1, 2);
        Page<String> page = new PageImpl<>(List.of("c", "d"), pageable, 5);

        ApiResponse<List<String>> response = PageResponse.of(page);

        assertEquals(List.of("c", "d"), response.data());
        assertEquals(new PageMeta(1, 2, 5, 3), response.meta());
    }

    @Test
    void pageResponse_mapsContentKeepingMeta() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 3), 3);

        ApiResponse<List<String>> response = PageResponse.of(page, n -> "n" + n);

        assertEquals(List.of("n1", "n2", "n3"), response.data());
        assertEquals(new PageMeta(0, 3, 3, 1), response.meta());
    }

    @Test
    void singleResource_hasNullMeta() {
        ApiResponse<String> response = ApiResponse.of("x");
        assertEquals("x", response.data());
        assertNull(response.meta());
    }
}
