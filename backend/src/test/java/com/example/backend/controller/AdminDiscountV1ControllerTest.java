package com.example.backend.controller;

import com.example.backend.dto.DiscountRequest;
import com.example.backend.entity.Discount;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.DiscountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B04: /api/v1/admin/discounts through standalone MockMvc (real controller, real AuthGuard, real GlobalExceptionHandler,
 * mocked DiscountService): the permission matrix (401 / 403 / success), validation, paging and error mapping. The request
 * attribute stands in for what AuthInterceptor sets from a verified token.
 */
class AdminDiscountV1ControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String BODY = "{\"code\":\"SALE10\",\"percentOff\":10,\"minOrderAmount\":200000}";

    private DiscountService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DiscountService.class);
        AdminDiscountV1Controller controller = new AdminDiscountV1Controller();
        ReflectionTestUtils.setField(controller, "discountService", service);
        ReflectionTestUtils.setField(controller, "authGuard", new AuthGuard());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();

        when(service.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(discount(1L)), PageRequest.of(0, 20), 1));
        when(service.get(1L)).thenReturn(discount(1L));
        when(service.create(any(DiscountRequest.class))).thenReturn(discount(1L));
        when(service.update(anyLong(), any(DiscountRequest.class))).thenReturn(discount(1L));
    }

    private static Discount discount(long id) {
        Discount d = new Discount();
        d.setId(id);
        d.setCode("SALE10");
        d.setPercentOff(10);
        d.setMinOrderAmount(new BigDecimal("200000.00"));
        d.setActive(true);
        return d;
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String... permissions) {
        return builder.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(7L, List.of("MANAGER"), List.of(permissions)));
    }

    private int statusOf(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder).andReturn().getResponse().getStatus();
    }

    // ---- adminDiscounts_needPermission ----

    @Test
    void adminDiscounts_needPermission_withoutToken_is401() throws Exception {
        assertEquals(401, statusOf(get("/api/v1/admin/discounts")));
        assertEquals(401, statusOf(get("/api/v1/admin/discounts/1")));
        assertEquals(401, statusOf(post("/api/v1/admin/discounts").contentType(JSON).content(BODY)));
        assertEquals(401, statusOf(put("/api/v1/admin/discounts/1").contentType(JSON).content(BODY)));
        assertEquals(401, statusOf(delete("/api/v1/admin/discounts/1")));

        verifyNoInteractions(service);
    }

    @Test
    void adminDiscounts_needPermission_aCustomerOrAnUnrelatedPermission_is403() throws Exception {
        // a customer holds no permission at all
        assertEquals(403, statusOf(as(get("/api/v1/admin/discounts"))));
        // an unrelated permission is no help either
        for (String other : new String[] {Permissions.ORDER_VIEW_ALL, Permissions.PRODUCT_UPDATE, Permissions.INVENTORY_VIEW}) {
            assertEquals(403, statusOf(as(get("/api/v1/admin/discounts"), other)), other);
            assertEquals(403, statusOf(as(get("/api/v1/admin/discounts/1"), other)), other);
            assertEquals(403, statusOf(as(post("/api/v1/admin/discounts").contentType(JSON).content(BODY), other)), other);
            assertEquals(403, statusOf(as(put("/api/v1/admin/discounts/1").contentType(JSON).content(BODY), other)), other);
            assertEquals(403, statusOf(as(delete("/api/v1/admin/discounts/1"), other)), other);
        }

        verifyNoInteractions(service);
    }

    @Test
    void adminDiscounts_needPermission_viewCanReadButNotChange() throws Exception {
        assertEquals(200, statusOf(as(get("/api/v1/admin/discounts"), Permissions.DISCOUNT_VIEW)));
        assertEquals(200, statusOf(as(get("/api/v1/admin/discounts/1"), Permissions.DISCOUNT_VIEW)));

        assertEquals(403, statusOf(as(post("/api/v1/admin/discounts").contentType(JSON).content(BODY), Permissions.DISCOUNT_VIEW)));
        assertEquals(403, statusOf(as(put("/api/v1/admin/discounts/1").contentType(JSON).content(BODY), Permissions.DISCOUNT_VIEW)));
        assertEquals(403, statusOf(as(delete("/api/v1/admin/discounts/1"), Permissions.DISCOUNT_VIEW)));
        verify(service, never()).create(any());
        verify(service, never()).update(anyLong(), any());
        verify(service, never()).delete(anyLong());
    }

    @Test
    void adminDiscounts_needPermission_manageCanChangeButTheTwoDoNotImplyEachOther() throws Exception {
        assertEquals(201, statusOf(as(post("/api/v1/admin/discounts").contentType(JSON).content(BODY), Permissions.DISCOUNT_MANAGE)));
        assertEquals(200, statusOf(as(put("/api/v1/admin/discounts/1").contentType(JSON).content(BODY), Permissions.DISCOUNT_MANAGE)));
        assertEquals(204, statusOf(as(delete("/api/v1/admin/discounts/1"), Permissions.DISCOUNT_MANAGE)));

        // manage alone does not read
        assertEquals(403, statusOf(as(get("/api/v1/admin/discounts"), Permissions.DISCOUNT_MANAGE)));
        assertEquals(403, statusOf(as(get("/api/v1/admin/discounts/1"), Permissions.DISCOUNT_MANAGE)));
        verify(service).delete(1L);
    }

    // ---- success shapes ----

    @Test
    void list_isPaged_inTheEnvelope_withTheStandardDefaults() throws Exception {
        mvc.perform(as(get("/api/v1/admin/discounts"), Permissions.DISCOUNT_VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].code").value("SALE10"))
                .andExpect(jsonPath("$.data[0].percentOff").value(10))
                .andExpect(jsonPath("$.data[0].minOrderAmount").value(200000.00))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt", "id"), pageable.getValue().getSort());
    }

    @Test
    void list_sizeAbove100_isClamped_andAnUnknownSortField_is400() throws Exception {
        mvc.perform(as(get("/api/v1/admin/discounts").param("size", "1000"), Permissions.DISCOUNT_VIEW)).andExpect(status().isOk());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize());

        mvc.perform(as(get("/api/v1/admin/discounts").param("sort", "percentOff,asc"), Permissions.DISCOUNT_VIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_is201_andReadingAMissingOneIs404() throws Exception {
        mvc.perform(as(post("/api/v1/admin/discounts").contentType(JSON).content(BODY), Permissions.DISCOUNT_MANAGE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SALE10"));

        ArgumentCaptor<DiscountRequest> request = ArgumentCaptor.forClass(DiscountRequest.class);
        verify(service).create(request.capture());
        assertEquals("SALE10", request.getValue().code());
        assertEquals(Integer.valueOf(10), request.getValue().percentOff());

        when(service.get(9L)).thenThrow(new ResourceNotFoundException("Không tìm thấy mã giảm giá #9"));
        mvc.perform(as(get("/api/v1/admin/discounts/9"), Permissions.DISCOUNT_VIEW))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ---- validation and error mapping ----

    @Test
    void create_validation_is400WithFields() throws Exception {
        String[] bad = {
                "{\"percentOff\":10}",                                   // no code
                "{\"code\":\"  \",\"percentOff\":10}",                   // blank code
                "{\"code\":\"HAS SPACE\",\"percentOff\":10}",            // characters outside letters, digits, - and _
                "{\"code\":\"" + "A".repeat(31) + "\",\"percentOff\":10}", // too long
                "{\"code\":\"X\",\"percentOff\":0}",                     // percent below 1
                "{\"code\":\"X\",\"percentOff\":101}",                   // percent above 100
                "{\"code\":\"X\",\"amountOff\":0}",                      // amount not above 0
                "{\"code\":\"X\",\"amountOff\":-5}",
                "{\"code\":\"X\",\"percentOff\":5,\"minOrderAmount\":-1}" // negative minimum
        };
        for (String body : bad) {
            mvc.perform(as(post("/api/v1/admin/discounts").contentType(JSON).content(body), Permissions.DISCOUNT_MANAGE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        verify(service, never()).create(any());
    }

    @Test
    void create_bothOrNeitherOfPercentAndAmount_isTheServicesValidationError() throws Exception {
        when(service.create(any(DiscountRequest.class)))
                .thenThrow(new BadRequestException("Chỉ nhập một trong hai: phần trăm giảm hoặc số tiền giảm."));

        mvc.perform(as(post("/api/v1/admin/discounts").contentType(JSON).content("{\"code\":\"X\",\"percentOff\":5,\"amountOff\":1000}"),
                        Permissions.DISCOUNT_MANAGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_duplicateCode_is409() throws Exception {
        when(service.create(any(DiscountRequest.class))).thenThrow(new ConflictException("Mã giảm giá \"SALE10\" đã tồn tại."));

        mvc.perform(as(post("/api/v1/admin/discounts").contentType(JSON).content(BODY), Permissions.DISCOUNT_MANAGE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }
}
