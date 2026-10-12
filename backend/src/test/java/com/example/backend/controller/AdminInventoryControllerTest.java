package com.example.backend.controller;

import com.example.backend.dto.CartResponse;
import com.example.backend.dto.InventoryResponse;
import com.example.backend.dto.ProductResponse;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.InventoryRepository.InventoryRow;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.inventory.InventoryAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B03 admin inventory endpoints through the real GlobalExceptionHandler and the real AuthGuard (standalone MockMvc, mocked
 * service). The request attribute stands in for what AuthInterceptor sets from a verified token.
 */
class AdminInventoryControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String ADJUST_BODY = "{\"quantityDelta\":5,\"reason\":\"recount\"}";

    private InventoryAdminService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(InventoryAdminService.class);
        AdminInventoryV1Controller controller = new AdminInventoryV1Controller();
        ReflectionTestUtils.setField(controller, "inventoryAdminService", service);
        ReflectionTestUtils.setField(controller, "authGuard", new AuthGuard());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();

        when(service.list(any(), anyBoolean(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row(7L, 10, 3))));
        when(service.get(7L)).thenReturn(row(7L, 10, 3));
        when(service.ledger(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.adjust(eq(7L), anyInt(), anyString(), any())).thenReturn(row(7L, 15, 3));
    }

    private static InventoryRow row(long productId, int onHand, int reserved) {
        return new InventoryRow() {
            public Long getProductId() { return productId; }
            public String getProductName() { return "Bình giữ nhiệt"; }
            public String getSku() { return "SKU-1"; }
            public Integer getQuantityOnHand() { return onHand; }
            public Integer getReservedQuantity() { return reserved; }
            public LocalDateTime getUpdatedAt() { return null; }
        };
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String... permissions) {
        return b.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(42L, List.of("MANAGER"), List.of(permissions)));
    }

    private int statusOf(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    // ── permission matrix ────────────────────────────────────────────────────

    @Test
    void everyEndpoint_withoutToken_is401() throws Exception {
        assertEquals(401, statusOf(get("/api/v1/admin/inventory")));
        assertEquals(401, statusOf(get("/api/v1/admin/inventory/7")));
        assertEquals(401, statusOf(get("/api/v1/admin/inventory/7/transactions")));
        assertEquals(401, statusOf(patch("/api/v1/admin/inventory/7/adjust").contentType(JSON).content(ADJUST_BODY)));
        verify(service, never()).adjust(any(), anyInt(), any(), any());
    }

    @Test
    void everyEndpoint_withAnUnrelatedPermission_is403() throws Exception {
        assertEquals(403, statusOf(as(get("/api/v1/admin/inventory"), Permissions.ORDER_VIEW)));
        assertEquals(403, statusOf(as(get("/api/v1/admin/inventory/7"), Permissions.PRODUCT_VIEW)));
        assertEquals(403, statusOf(as(get("/api/v1/admin/inventory/7/transactions"), Permissions.PRODUCT_VIEW)));
        assertEquals(403, statusOf(as(patch("/api/v1/admin/inventory/7/adjust").contentType(JSON).content(ADJUST_BODY), Permissions.PRODUCT_UPDATE)));
    }

    @Test
    void viewing_needsInventoryView_andAdjusting_needsInventoryAdjust_notEachOther() throws Exception {
        assertEquals(200, statusOf(as(get("/api/v1/admin/inventory"), Permissions.INVENTORY_VIEW)));
        assertEquals(200, statusOf(as(get("/api/v1/admin/inventory/7"), Permissions.INVENTORY_VIEW)));
        assertEquals(200, statusOf(as(get("/api/v1/admin/inventory/7/transactions"), Permissions.INVENTORY_VIEW)));

        // a viewer cannot adjust; the service must not even be called
        assertEquals(403, statusOf(as(patch("/api/v1/admin/inventory/7/adjust").contentType(JSON).content(ADJUST_BODY), Permissions.INVENTORY_VIEW)));
        verify(service, never()).adjust(any(), anyInt(), any(), any());
        // inventory:update (a different code) is not enough either
        assertEquals(403, statusOf(as(patch("/api/v1/admin/inventory/7/adjust").contentType(JSON).content(ADJUST_BODY), Permissions.INVENTORY_UPDATE)));

        assertEquals(200, statusOf(as(patch("/api/v1/admin/inventory/7/adjust").contentType(JSON).content(ADJUST_BODY), Permissions.INVENTORY_ADJUST)));
        // adjusting alone does not grant the list
        assertEquals(403, statusOf(as(get("/api/v1/admin/inventory"), Permissions.INVENTORY_ADJUST)));
    }

    // ── behaviour ────────────────────────────────────────────────────────────

    @Test
    void adjust_recordsTheActor_andReturnsAvailableAndReserved() throws Exception {
        mvc.perform(as(patch("/api/v1/admin/inventory/7/adjust").contentType(JSON).content(ADJUST_BODY), Permissions.INVENTORY_ADJUST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(7))
                .andExpect(jsonPath("$.data.quantityOnHand").value(15))
                .andExpect(jsonPath("$.data.reservedQuantity").value(3))
                .andExpect(jsonPath("$.data.available").value(12));

        verify(service).adjust(7L, 5, "recount", 42L);
    }

    @Test
    void adjust_missingOrBlankOrOverlongReason_orMissingDelta_is400_andNothingRuns() throws Exception {
        for (String body : List.of(
                "{\"quantityDelta\":5}",
                "{\"quantityDelta\":5,\"reason\":\"\"}",
                "{\"quantityDelta\":5,\"reason\":\"   \"}",
                "{\"quantityDelta\":5,\"reason\":\"" + "x".repeat(256) + "\"}",
                "{\"reason\":\"recount\"}")) {
            assertEquals(400, statusOf(as(patch("/api/v1/admin/inventory/7/adjust").contentType(JSON).content(body), Permissions.INVENTORY_ADJUST)), body);
        }
        verify(service, never()).adjust(any(), anyInt(), any(), any());
    }

    @Test
    void adjust_belowReserved_is400BusinessRuleViolation() throws Exception {
        when(service.adjust(eq(7L), eq(-9), anyString(), any()))
                .thenThrow(new BusinessRuleViolationException("Tồn kho sau điều chỉnh không được thấp hơn số lượng đang được giữ."));

        mvc.perform(as(patch("/api/v1/admin/inventory/7/adjust").contentType(JSON)
                        .content("{\"quantityDelta\":-9,\"reason\":\"damaged\"}"), Permissions.INVENTORY_ADJUST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void detail_ofAProductWithoutInventory_is404() throws Exception {
        when(service.get(99L)).thenThrow(new ResourceNotFoundException("Không tìm thấy tồn kho của sản phẩm."));

        mvc.perform(as(get("/api/v1/admin/inventory/99"), Permissions.INVENTORY_VIEW)).andExpect(status().isNotFound());
    }

    @Test
    void list_passesTheFiltersThrough_andReturnsThePageMeta() throws Exception {
        mvc.perform(as(get("/api/v1/admin/inventory").param("keyword", "bình").param("lowStock", "true").param("threshold", "5"),
                        Permissions.INVENTORY_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].available").value(7))
                .andExpect(jsonPath("$.meta.page").value(0));

        verify(service).list(eq("bình"), eq(true), eq(5), any(Pageable.class));
    }

    @Test
    void list_withoutLowStockParam_meansNoLowStockFilter() throws Exception {
        mvc.perform(as(get("/api/v1/admin/inventory"), Permissions.INVENTORY_VIEW)).andExpect(status().isOk());

        verify(service).list(any(), eq(false), any(), any(Pageable.class));
    }

    // ── reserved is never public ─────────────────────────────────────────────

    @Test
    void publicAndCartResponses_neverContainReservedOrOnHand() throws Exception {
        Product p = new Product();
        p.setId(1L);
        p.setName("Túi");
        p.setSlug("tui");
        p.setPrice(new BigDecimal("1000"));
        p.setStatus(Product.Status.ACTIVE);
        CartItem item = new CartItem();
        item.setId(2L);
        item.setProduct(p);
        item.setQuantity(1);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        String productJson = mapper.writeValueAsString(ProductResponse.from(p, 3));
        String cartJson = mapper.writeValueAsString(CartResponse.from(1L, List.of(item), Map.of(1L, 3)));

        for (String json : List.of(productJson, cartJson)) {
            assertFalse(json.toLowerCase().contains("reserved"), json);
            assertFalse(json.toLowerCase().contains("onhand"), json);
            assertTrue(json.contains("\"stockQuantity\":3"), json);
        }
        // and the admin DTO is the only one that carries them
        assertTrue(mapper.writeValueAsString(InventoryResponse.from(row(1L, 10, 3))).contains("reservedQuantity"));
    }
}
