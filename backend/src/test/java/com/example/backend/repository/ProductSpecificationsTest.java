package com.example.backend.repository;

import com.example.backend.dto.ProductSearchCriteria;
import com.example.backend.entity.Product;
import com.example.backend.exception.BadRequestException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks which predicates the Specification BUILDS (against mocked Criteria objects). It does not execute SQL, so it
 * proves parameterisation/escaping/filter wiring, not the rows a real PostgreSQL returns.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ProductSpecificationsTest {

    private final Root root = mock(Root.class);
    private final CriteriaQuery query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Path path = mock(Path.class);
    private final Expression lowered = mock(Expression.class);

    @BeforeEach
    void stubCriteria() {
        when(root.get(anyString())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);
        when(cb.lower(any(Expression.class))).thenReturn(lowered);
    }

    private void apply(ProductSearchCriteria criteria, Product.Status status) {
        Specification<Product> spec = ProductSpecifications.matching(criteria, status);
        spec.toPredicate(root, query, cb);
    }

    @Test
    void escapeLike_escapesBackslashPercentAndUnderscore() {
        assertEquals("50\\%\\_off\\\\", ProductSpecifications.escapeLike("50%_off\\"));
        assertEquals("plain", ProductSpecifications.escapeLike("plain"));
    }

    @Test
    void search_keywordWithPercentAndUnderscore_isLiteral() {
        apply(new ProductSearchCriteria("50%_Off\\", null, null, null, null, null), null);

        // name and description are both matched, with the escaped, lower-cased keyword and '\' as the escape character
        verify(cb, times(2)).like(any(Expression.class), eq("%50\\%\\_off\\\\%"), eq('\\'));
    }

    @Test
    void search_blankKeyword_addsNoLikeFilter() {
        apply(new ProductSearchCriteria("   ", null, null, null, null, null), null);

        verify(cb, never()).like(any(Expression.class), anyString(), anyChar());
        verify(cb, never()).lower(any(Expression.class));
    }

    @Test
    void search_priceRange_buildsBothBounds() {
        apply(new ProductSearchCriteria(null, null, new BigDecimal("100"), new BigDecimal("500"), null, null), null);

        verify(cb).greaterThanOrEqualTo(any(Expression.class), eq(new BigDecimal("100")));
        verify(cb).lessThanOrEqualTo(any(Expression.class), eq(new BigDecimal("500")));
    }

    @Test
    void search_onlyMinPrice_hasNoUpperBound() {
        apply(new ProductSearchCriteria(null, null, new BigDecimal("100"), null, null, null), null);

        verify(cb).greaterThanOrEqualTo(any(Expression.class), eq(new BigDecimal("100")));
        verify(cb, never()).lessThanOrEqualTo(any(Expression.class), any(BigDecimal.class));
    }

    @Test
    void search_inStockTrue_requiresPositiveStock_falseAddsNothing() {
        apply(new ProductSearchCriteria(null, null, null, null, true, null), null);
        verify(cb).greaterThan(any(Expression.class), eq(0));

        org.mockito.Mockito.clearInvocations(cb);
        apply(new ProductSearchCriteria(null, null, null, null, false, null), null);
        verify(cb, never()).greaterThan(any(Expression.class), any(Integer.class));
    }

    @Test
    void search_categoryId_isBoundAsParameter() {
        apply(new ProductSearchCriteria(null, 7L, null, null, null, null), null);

        verify(cb).equal(any(Expression.class), eq(7L));
    }

    @Test
    void statusFilter_isEqualityOnTheGivenStatus_orAbsent() {
        apply(ProductSearchCriteria.none(), Product.Status.ACTIVE);
        verify(cb).equal(any(Expression.class), eq(Product.Status.ACTIVE));

        org.mockito.Mockito.clearInvocations(cb);
        apply(ProductSearchCriteria.none(), null);
        verify(cb, never()).equal(any(Expression.class), any(Object.class));
    }

    @Test
    void criteria_rejectsNegativePricesInvertedRangeAndOverlongKeyword() {
        assertThrows(BadRequestException.class,
                () -> new ProductSearchCriteria(null, null, new BigDecimal("-1"), null, null, null));
        assertThrows(BadRequestException.class,
                () -> new ProductSearchCriteria(null, null, new BigDecimal("10"), new BigDecimal("5"), null, null));
        assertThrows(BadRequestException.class,
                () -> new ProductSearchCriteria("x".repeat(101), null, null, null, null, null));
    }
}
