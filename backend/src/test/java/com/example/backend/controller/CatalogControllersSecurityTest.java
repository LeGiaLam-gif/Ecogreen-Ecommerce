package com.example.backend.controller;

import com.example.backend.dto.CategoryRequest;
import com.example.backend.dto.ProductCreateRequest;
import com.example.backend.dto.ProductUpdateRequest;
import com.example.backend.entity.Category;
import com.example.backend.entity.Product;
import com.example.backend.entity.ProductImage;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.CategoryService;
import com.example.backend.service.ProductImageService;
import com.example.backend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Security + contract tests for the B02 controllers (standalone MockMvc, mocked services, real AuthGuard and real
 * GlobalExceptionHandler). The request attribute stands in for what AuthInterceptor sets from a verified token.
 */
class CatalogControllersSecurityTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final List<String> ALL = List.of(
            Permissions.PRODUCT_VIEW, Permissions.PRODUCT_CREATE, Permissions.PRODUCT_UPDATE, Permissions.PRODUCT_DELETE,
            Permissions.PRODUCT_PUBLISH, Permissions.CATEGORY_VIEW, Permissions.CATEGORY_CREATE, Permissions.CATEGORY_UPDATE,
            Permissions.CATEGORY_DELETE);

    private ProductService productService;
    private ProductImageService productImageService;
    private CategoryService categoryService;
    private MockMvc mvc;
    private Product product;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        productImageService = mock(ProductImageService.class);
        categoryService = mock(CategoryService.class);

        Category category = new Category();
        category.setId(1L);
        category.setName("Nhà bếp");
        product = new Product();
        product.setId(7L);
        product.setName("Bình giữ nhiệt");
        product.setSlug("binh-giu-nhiet");
        product.setPrice(new BigDecimal("250000"));
        product.setCategory(category);
        product.setStatus(Product.Status.ACTIVE);

        ProductImage image = new ProductImage();
        image.setId(21L);
        image.setImageUrl("/api/v1/files/0123456789abcdef0123456789abcdef.png");

        when(productService.searchAdmin(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(product)));
        when(productService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(product)));
        when(productService.create(any(ProductCreateRequest.class))).thenReturn(product);
        when(productService.update(eq(7L), any(ProductUpdateRequest.class))).thenReturn(product);
        when(productService.publish(7L)).thenReturn(product);
        when(productService.getByIdOrSlug("binh-giu-nhiet", true)).thenReturn(product);
        when(productImageService.upload(eq(7L), any(byte[].class), any())).thenReturn(image);
        when(categoryService.create(any(CategoryRequest.class))).thenReturn(category);
        when(categoryService.update(eq(1L), any(CategoryRequest.class))).thenReturn(category);

        AuthGuard guard = new AuthGuard();
        AdminProductV1Controller adminProducts = new AdminProductV1Controller();
        ReflectionTestUtils.setField(adminProducts, "productService", productService);
        ReflectionTestUtils.setField(adminProducts, "productImageService", productImageService);
        ReflectionTestUtils.setField(adminProducts, "authGuard", guard);
        AdminCategoryV1Controller adminCategories = new AdminCategoryV1Controller();
        ReflectionTestUtils.setField(adminCategories, "categoryService", categoryService);
        ReflectionTestUtils.setField(adminCategories, "authGuard", guard);
        ProductV1Controller publicProducts = new ProductV1Controller();
        ReflectionTestUtils.setField(publicProducts, "productService", productService);
        CategoryV1Controller publicCategories = new CategoryV1Controller();
        ReflectionTestUtils.setField(publicCategories, "categoryService", categoryService);

        mvc = MockMvcBuilders.standaloneSetup(adminProducts, adminCategories, publicProducts, publicCategories)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static CurrentUser userWith(List<String> permissions) {
        return new CurrentUser(1L, List.of("MANAGER"), permissions);
    }

    private static List<String> allExcept(String code) {
        List<String> l = new ArrayList<>(ALL);
        l.remove(code);
        return l;
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, List<String> permissions) {
        return b.requestAttr(AuthInterceptor.REQUEST_ATTR, userWith(permissions));
    }

    /** One entry per permission-sensitive endpoint: how to call it, and the single permission it requires. */
    private record Endpoint(String name, Supplier<MockHttpServletRequestBuilder> request, String permission) {
    }

    private List<Endpoint> endpoints() {
        MockMultipartFile png = new MockMultipartFile("file", "x.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        return List.of(
                new Endpoint("GET admin products", () -> get("/api/v1/admin/products"), Permissions.PRODUCT_VIEW),
                new Endpoint("POST admin product", () -> post("/api/v1/admin/products").contentType(JSON)
                        .content("{\"name\":\"Bình\",\"price\":100000,\"stockQuantity\":5,\"categoryId\":1}"), Permissions.PRODUCT_CREATE),
                new Endpoint("PATCH admin product", () -> patch("/api/v1/admin/products/7").contentType(JSON)
                        .content("{\"name\":\"Bình mới\"}"), Permissions.PRODUCT_UPDATE),
                new Endpoint("POST publish", () -> post("/api/v1/admin/products/7/publish"), Permissions.PRODUCT_PUBLISH),
                new Endpoint("DELETE admin product", () -> delete("/api/v1/admin/products/7"), Permissions.PRODUCT_DELETE),
                new Endpoint("POST image", () -> multipart("/api/v1/admin/products/7/images").file(png), Permissions.PRODUCT_UPDATE),
                new Endpoint("DELETE image", () -> delete("/api/v1/admin/products/7/images/21"), Permissions.PRODUCT_UPDATE),
                new Endpoint("POST admin category", () -> post("/api/v1/admin/categories").contentType(JSON)
                        .content("{\"name\":\"Mới\"}"), Permissions.CATEGORY_CREATE),
                new Endpoint("PATCH admin category", () -> patch("/api/v1/admin/categories/1").contentType(JSON)
                        .content("{\"name\":\"Đổi tên\"}"), Permissions.CATEGORY_UPDATE),
                new Endpoint("DELETE admin category", () -> delete("/api/v1/admin/categories/1"), Permissions.CATEGORY_DELETE));
    }

    // ── permission matrix ────────────────────────────────────────────────────

    @Test
    void adminEndpoints_withoutToken_are401() throws Exception {
        for (Endpoint e : endpoints()) {
            int status = mvc.perform(e.request().get()).andReturn().getResponse().getStatus();
            assertEquals(401, status, e.name());
        }
    }

    @Test
    void adminEndpoints_withUnrelatedPermission_are403_andNothingRuns() throws Exception {
        for (Endpoint e : endpoints()) {
            int status = mvc.perform(as(e.request().get(), List.of(Permissions.ORDER_VIEW))).andReturn().getResponse().getStatus();
            assertEquals(403, status, e.name());
        }
        verify(productService, never()).create(any(ProductCreateRequest.class));
        verify(productService, never()).publish(any());
        verify(productService, never()).delete(any());
        verify(categoryService, never()).delete(any());
        verify(productImageService, never()).upload(any(), any(byte[].class), any());
    }

    @Test
    void eachAdminEndpoint_needsItsOwnPermission_denyingOnlyThatOneIs403_grantingOnlyThatOneIsNot() throws Exception {
        for (Endpoint e : endpoints()) {
            int denied = mvc.perform(as(e.request().get(), allExcept(e.permission()))).andReturn().getResponse().getStatus();
            assertEquals(403, denied, e.name() + " must be denied without " + e.permission());

            int allowed = mvc.perform(as(e.request().get(), List.of(e.permission()))).andReturn().getResponse().getStatus();
            assertTrue(allowed >= 200 && allowed < 300, e.name() + " must succeed with only " + e.permission() + " but was " + allowed);
        }
    }

    // ── public endpoints ─────────────────────────────────────────────────────

    @Test
    void publicEndpoints_needNoToken() throws Exception {
        assertEquals(200, mvc.perform(get("/api/v1/products")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/v1/products/binh-giu-nhiet")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/v1/categories")).andReturn().getResponse().getStatus());
        mvc.perform(get("/api/v1/categories").param("tree", "true"));
        verify(categoryService).getTree();
    }

    @Test
    void publicDetail_ofNonActiveOrUnknownProduct_is404() throws Exception {
        when(productService.getByIdOrSlug("draft-one", true)).thenThrow(new ResourceNotFoundException("Không tìm thấy sản phẩm."));
        assertEquals(404, mvc.perform(get("/api/v1/products/draft-one")).andReturn().getResponse().getStatus());
    }

    @Test
    void publicList_neverLetsTheCallerChooseAStatus_andClampsSize() throws Exception {
        mvc.perform(get("/api/v1/products").param("size", "1000").param("status", "DRAFT"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<com.example.backend.dto.ProductSearchCriteria> criteria =
                ArgumentCaptor.forClass(com.example.backend.dto.ProductSearchCriteria.class);
        verify(productService).search(criteria.capture(), pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize());
        assertNull(criteria.getValue().status()); // the public endpoint has no status parameter at all
    }

    @Test
    void list_sortNotWhitelisted_is400_andNeverReachesTheService() throws Exception {
        assertEquals(400, mvc.perform(get("/api/v1/products").param("sort", "stockQuantity,asc")).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(as(get("/api/v1/admin/products").param("sort", "price; drop table products"),
                List.of(Permissions.PRODUCT_VIEW))).andReturn().getResponse().getStatus());
        verify(productService, never()).search(any(), any(Pageable.class));
        verify(productService, never()).searchAdmin(any(), any(Pageable.class));
    }

    // ── validation and error mapping ─────────────────────────────────────────

    @Test
    void create_invalidBody_is400ValidationError() throws Exception {
        String[] bodies = {
                "{\"name\":\"\",\"price\":1,\"stockQuantity\":1,\"categoryId\":1}",
                "{\"name\":\"x\",\"price\":-5,\"stockQuantity\":1,\"categoryId\":1}",
                "{\"name\":\"x\",\"price\":1,\"stockQuantity\":-1,\"categoryId\":1}",
                "{\"name\":\"x\",\"price\":1,\"stockQuantity\":1}",
                "{\"name\":\"x\",\"price\":1,\"stockQuantity\":1,\"categoryId\":1,\"status\":\"HACKED\"}"
        };
        for (String body : bodies) {
            int status = mvc.perform(as(post("/api/v1/admin/products").contentType(JSON).content(body),
                    List.of(Permissions.PRODUCT_CREATE))).andReturn().getResponse().getStatus();
            assertEquals(400, status, body);
        }
        verify(productService, never()).create(any(ProductCreateRequest.class));
    }

    @Test
    void publish_ruleViolation_is400BusinessRule() throws Exception {
        when(productService.publish(8L)).thenThrow(new BusinessRuleViolationException("Chưa thể xuất bản sản phẩm, còn thiếu: ít nhất một hình ảnh."));
        mvc.perform(as(post("/api/v1/admin/products/8/publish"), List.of(Permissions.PRODUCT_PUBLISH)))
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void category_deleteWithProducts_is409Conflict() throws Exception {
        doThrow(new ConflictException("Không thể xóa danh mục đang có sản phẩm.")).when(categoryService).delete(2L);
        mvc.perform(as(delete("/api/v1/admin/categories/2"), List.of(Permissions.CATEGORY_DELETE)))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        assertEquals(409, mvc.perform(as(delete("/api/v1/admin/categories/2"), List.of(Permissions.CATEGORY_DELETE)))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void upload_withoutFilePart_is400_afterThePermissionCheck() throws Exception {
        assertEquals(403, mvc.perform(as(multipart("/api/v1/admin/products/7/images"), List.of(Permissions.PRODUCT_VIEW)))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(as(multipart("/api/v1/admin/products/7/images"), List.of(Permissions.PRODUCT_UPDATE)))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void adminList_passesTheStatusFilter_publicSearchDoesNot() throws Exception {
        mvc.perform(as(get("/api/v1/admin/products").param("status", "DRAFT"), List.of(Permissions.PRODUCT_VIEW)));
        ArgumentCaptor<com.example.backend.dto.ProductSearchCriteria> c =
                ArgumentCaptor.forClass(com.example.backend.dto.ProductSearchCriteria.class);
        verify(productService).searchAdmin(c.capture(), any(Pageable.class));
        assertEquals(Product.Status.DRAFT, c.getValue().status());
    }
}
