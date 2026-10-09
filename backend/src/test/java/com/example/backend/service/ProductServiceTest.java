package com.example.backend.service;

import com.example.backend.dto.ProductCreateRequest;
import com.example.backend.dto.ProductSearchCriteria;
import com.example.backend.dto.ProductUpdateRequest;
import com.example.backend.entity.Category;
import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ProductImageRepository;
import com.example.backend.repository.ProductRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B02 ProductService behaviour with mocked repositories (no database, no Spring context). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "rawtypes"})
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private CategoryService categoryService;
    @InjectMocks private ProductService service;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(5L);
        category.setName("Nhà bếp");
        when(categoryService.getById(5L)).thenReturn(category);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ProductCreateRequest create(String name, Product.Status status) {
        return new ProductCreateRequest(name, "mô tả", new BigDecimal("100000"), null, 5, null, 5L, null, null, status);
    }

    private Product product(Long id, Product.Status status, String image) {
        Product p = new Product();
        p.setId(id);
        p.setName("Bình giữ nhiệt");
        p.setSlug("binh-giu-nhiet");
        p.setPrice(new BigDecimal("250000"));
        p.setCategory(category);
        p.setImage(image);
        p.setStatus(status);
        when(productRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    // ── create / delete ──────────────────────────────────────────────────────

    @Test
    void create_defaultsToActive() {
        Product p = service.create(create("Bình giữ nhiệt", null));
        assertEquals(Product.Status.ACTIVE, p.getStatus());
        assertEquals("binh-giu-nhiet", p.getSlug());
    }

    @Test
    void create_draftIsOptIn_andOtherStatusesAreRejected() {
        assertEquals(Product.Status.DRAFT, service.create(create("Bình A", Product.Status.DRAFT)).getStatus());
        for (Product.Status s : new Product.Status[]{Product.Status.INACTIVE, Product.Status.ARCHIVED, Product.Status.OUT_OF_STOCK}) {
            assertThrows(BadRequestException.class, () -> service.create(create("Bình B", s)));
        }
    }

    @Test
    void slug_vietnameseName_isTransliteratedAndUnique() {
        when(productRepository.existsBySlug("ao-thun-dep")).thenReturn(true);
        when(productRepository.existsBySlug("ao-thun-dep-2")).thenReturn(false);

        Product p = service.create(create("Áo Thun Đẹp", null));

        assertEquals("ao-thun-dep-2", p.getSlug());
    }

    @Test
    void slug_numericOnly_isRejectedOrPrefixed() {
        Product p = service.create(create("12345", null));
        assertEquals("product-12345", p.getSlug());
    }

    @Test
    void create_comparePriceBelowPrice_isRejected() {
        ProductCreateRequest r = new ProductCreateRequest("Bình", null, new BigDecimal("100"), new BigDecimal("99"),
                1, null, 5L, null, null, null);
        assertThrows(BadRequestException.class, () -> service.create(r));
        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void create_duplicateSku_isConflict() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);
        ProductCreateRequest r = new ProductCreateRequest("Bình", null, new BigDecimal("100"), null,
                1, null, 5L, "SKU-1", null, null);
        assertThrows(ConflictException.class, () -> service.create(r));
    }

    @Test
    void delete_setsInactive_notArchived() {
        Product p = product(9L, Product.Status.ACTIVE, "a.jpg");
        service.delete(9L);
        assertEquals(Product.Status.INACTIVE, p.getStatus());
        verify(productRepository).save(p);
    }

    // ── publish / patch-to-active ────────────────────────────────────────────

    @Test
    void publish_missingImage_throwsBusinessRule_andStaysDraft() {
        Product p = product(1L, Product.Status.DRAFT, null);
        when(productImageRepository.existsByProductId(1L)).thenReturn(false);

        assertThrows(BusinessRuleViolationException.class, () -> service.publish(1L));
        assertEquals(Product.Status.DRAFT, p.getStatus());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void publish_missingCategory_throwsBusinessRule() {
        Product p = product(2L, Product.Status.DRAFT, "a.jpg");
        p.setCategory(null);
        assertThrows(BusinessRuleViolationException.class, () -> service.publish(2L));
        assertEquals(Product.Status.DRAFT, p.getStatus());
    }

    @Test
    void publish_zeroPrice_throwsBusinessRule() {
        Product p = product(3L, Product.Status.INACTIVE, "a.jpg");
        p.setPrice(BigDecimal.ZERO);
        assertThrows(BusinessRuleViolationException.class, () -> service.publish(3L));
    }

    @Test
    void publish_withLegacyImageOrGalleryImage_activates_withoutSku() {
        Product legacy = product(4L, Product.Status.DRAFT, "bottle.jpg");
        assertEquals(Product.Status.ACTIVE, service.publish(4L).getStatus());
        assertEquals(null, legacy.getSku());

        Product gallery = product(5L, Product.Status.INACTIVE, null);
        when(productImageRepository.existsByProductId(5L)).thenReturn(true);
        assertEquals(Product.Status.ACTIVE, service.publish(5L).getStatus());
    }

    @Test
    void publish_archivedOrOutOfStock_isRejected() {
        product(6L, Product.Status.ARCHIVED, "a.jpg");
        product(7L, Product.Status.OUT_OF_STOCK, "a.jpg");
        assertThrows(BusinessRuleViolationException.class, () -> service.publish(6L));
        assertThrows(BusinessRuleViolationException.class, () -> service.publish(7L));
    }

    @Test
    void update_statusToActiveFromDraft_runsPublishChecks() {
        Product p = product(8L, Product.Status.DRAFT, null);
        when(productImageRepository.existsByProductId(8L)).thenReturn(false);
        ProductUpdateRequest r = new ProductUpdateRequest(null, null, null, null, null, null, null, null, null, null,
                Product.Status.ACTIVE);

        assertThrows(BusinessRuleViolationException.class, () -> service.update(8L, r));
        assertEquals(Product.Status.DRAFT, p.getStatus());
    }

    @Test
    void update_archive_isAllowedExplicitly_andRenameRegeneratesSlug() {
        Product p = product(10L, Product.Status.ACTIVE, "a.jpg");
        ProductUpdateRequest archive = new ProductUpdateRequest(null, null, null, null, null, null, null, null, null, null,
                Product.Status.ARCHIVED);
        assertEquals(Product.Status.ARCHIVED, service.update(10L, archive).getStatus());

        ProductUpdateRequest rename = new ProductUpdateRequest("Cốc tre", null, null, null, null, null, null, null, null, null, null);
        assertEquals("coc-tre", service.update(10L, rename).getSlug());
    }

    // ── read side ────────────────────────────────────────────────────────────

    @Test
    void search_sortNotWhitelisted_isRejected() {
        assertThrows(BadRequestException.class, () -> ProductService.toPageable(0, 12, "stockQuantity,asc"));
        assertThrows(BadRequestException.class, () -> ProductService.toPageable(0, 12, "price; drop table products,asc"));
        assertThrows(BadRequestException.class, () -> ProductService.toPageable(0, 12, "price,sideways"));
    }

    @Test
    void toPageable_defaultsSizeTo12_clampsTo100_andAddsIdTieBreaker() {
        Pageable d = ProductService.toPageable(null, null, null);
        assertEquals(12, d.getPageSize());
        assertEquals(0, d.getPageNumber());
        assertEquals(Sort.Direction.DESC, d.getSort().getOrderFor("createdAt").getDirection());
        assertNotNull(d.getSort().getOrderFor("id"));

        assertEquals(100, ProductService.toPageable(0, 5000, "price,desc").getPageSize());
        assertEquals(Sort.Direction.DESC, ProductService.toPageable(0, 10, "price,desc").getSort().getOrderFor("price").getDirection());
    }

    private void evaluate(Specification spec, Root root, CriteriaBuilder cb) {
        spec.toPredicate(root, mockQuery(), cb);
    }

    private static CriteriaQuery mockQuery() {
        return org.mockito.Mockito.mock(CriteriaQuery.class);
    }

    @Test
    void publicList_neverReturnsDraftInactiveArchived() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        // even if the caller asks for another status, the public search forces ACTIVE
        ProductSearchCriteria asksForDraft = new ProductSearchCriteria(null, null, null, null, null, Product.Status.DRAFT);

        service.search(asksForDraft, ProductService.toPageable(0, 12, null));

        ArgumentCaptor<Specification> captor = ArgumentCaptor.forClass(Specification.class);
        verify(productRepository).findAll(captor.capture(), any(Pageable.class));
        Root root = org.mockito.Mockito.mock(Root.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
        when(root.get(anyString())).thenReturn(path);
        evaluate(captor.getValue(), root, cb);

        verify(cb).equal(any(Expression.class), eq(Product.Status.ACTIVE));
        verify(cb, never()).equal(any(Expression.class), eq(Product.Status.DRAFT));
    }

    @Test
    void adminSearch_usesTheRequestedStatus_orNoStatusFilter() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.searchAdmin(new ProductSearchCriteria(null, null, null, null, null, Product.Status.DRAFT),
                ProductService.toPageable(0, 12, null));
        service.searchAdmin(ProductSearchCriteria.none(), ProductService.toPageable(0, 12, null));

        ArgumentCaptor<Specification> captor = ArgumentCaptor.forClass(Specification.class);
        verify(productRepository, times(2)).findAll(captor.capture(), any(Pageable.class));
        Root root = org.mockito.Mockito.mock(Root.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
        when(root.get(anyString())).thenReturn(path);

        evaluate(captor.getAllValues().get(0), root, cb);
        verify(cb).equal(any(Expression.class), eq(Product.Status.DRAFT));
        org.mockito.Mockito.clearInvocations(cb);
        evaluate(captor.getAllValues().get(1), root, cb);
        verify(cb, never()).equal(any(Expression.class), any(Object.class));
    }

    @Test
    void getByIdOrSlug_numericIsId_otherwiseSlug() {
        Product byId = product(42L, Product.Status.ACTIVE, "a.jpg");
        assertSame(byId, service.getByIdOrSlug("42", true));
        verify(productRepository, never()).findBySlug(anyString());

        Product bySlug = product(43L, Product.Status.ACTIVE, "a.jpg");
        when(productRepository.findBySlug("binh-giu-nhiet")).thenReturn(Optional.of(bySlug));
        assertSame(bySlug, service.getByIdOrSlug("binh-giu-nhiet", true));
    }

    @Test
    void getByIdOrSlug_publicHidesNonActive_adminSeesThem() {
        Product draft = product(44L, Product.Status.DRAFT, "a.jpg");
        assertThrows(ResourceNotFoundException.class, () -> service.getByIdOrSlug("44", true));
        assertSame(draft, service.getByIdOrSlug("44", false));
    }

    @Test
    void getByIdOrSlug_malformedKey_isNotFound_withoutQuerying() {
        for (String key : new String[]{"../etc/passwd", "a b", "UPPER", "x'; --", "", "99999999999999999999"}) {
            assertThrows(ResourceNotFoundException.class, () -> service.getByIdOrSlug(key, true));
        }
        verify(productRepository, never()).findBySlug(anyString());
        verify(productRepository, never()).findById(any(Long.class));
    }

    @Test
    void loadImages_isOneInQuery_groupedPerProduct_andSkippedForEmptyPage() {
        Product a = product(1L, Product.Status.ACTIVE, null);
        Product b = product(2L, Product.Status.ACTIVE, null);
        ProductImage i1 = image(10L, a, "u1");
        ProductImage i2 = image(11L, a, "u2");
        ProductImage i3 = image(12L, b, "u3");
        when(productImageRepository.findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(i1, i2, i3));

        Map<Long, List<ProductImage>> map = service.loadImages(List.of(a, b));

        assertEquals(List.of(i1, i2), map.get(1L));
        assertEquals(List.of(i3), map.get(2L));
        verify(productImageRepository, times(1)).findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(anyCollection());

        org.mockito.Mockito.clearInvocations(productImageRepository);
        assertTrue(service.loadImages(List.of()).isEmpty());
        verify(productImageRepository, never()).findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(anyCollection());
    }

    private static ProductImage image(Long id, Product p, String url) {
        ProductImage i = new ProductImage();
        i.setId(id);
        i.setProduct(p);
        i.setImageUrl(url);
        return i;
    }
}
