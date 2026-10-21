package com.example.backend.controller;

import com.example.backend.dto.ReturnDto;
import com.example.backend.entity.Order;
import com.example.backend.entity.Return;
import com.example.backend.entity.ReturnStatus;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.ReturnItemDraft;
import com.example.backend.service.ReturnService;
import com.example.backend.service.ReturnViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B07: the /api/v1 return endpoints through standalone MockMvc (real controller, real AuthGuard, the real
 * GlobalExceptionHandler, mocked ReturnService): authentication, the permission matrix with an allowed AND a denied caller,
 * the envelope, paging, the caller taken from the token, and the required staff note.
 */
class ReturnV1ControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String ITEMS = "[{\"orderItemId\":101,\"quantity\":1}]";

    private ReturnService returnService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        returnService = mock(ReturnService.class);
        ReturnV1Controller controller = new ReturnV1Controller();
        ReflectionTestUtils.setField(controller, "returnService", returnService);
        ReflectionTestUtils.setField(controller, "authGuard", new AuthGuard());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static CurrentUser customer() {
        return new CurrentUser(5L, List.of("CUSTOMER"), List.of());
    }

    private static CurrentUser staff(String... permissions) {
        return new CurrentUser(99L, List.of("MANAGER"), List.of(permissions));
    }

    private static ReturnDto dto(long id, ReturnStatus status) {
        Order order = new Order();
        order.setId(7L);
        Return request = new Return();
        request.setId(id);
        request.setOrder(order);
        request.setStatus(status);
        request.setReason("Sản phẩm bị lỗi");
        return ReturnDto.of(request, List.of(), List.of(), null, List.of("APPROVED"));
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder createRequest() {
        return multipart("/api/v1/returns")
                .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}))
                .file(new MockMultipartFile("images", "b.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}))
                .param("orderId", "7")
                .param("reason", "Sản phẩm bị lỗi")
                .param("description", "Bị vỡ")
                .param("items", ITEMS);
    }

    // ---- authentication ----

    @Test
    void requiresAuthentication_onEveryEndpoint() throws Exception {
        mvc.perform(createRequest()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/returns/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/returns/3")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/returns")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/returns/3/status").contentType(JSON).content("{\"newStatus\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(returnService);
    }

    // ---- POST /returns ----

    @Test
    void create_returns201WithTheEnvelope_andUsesTheTokenUserNotAClientId() throws Exception {
        when(returnService.create(anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(dto(50L, ReturnStatus.PENDING));

        mvc.perform(createRequest().param("userId", "6").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<byte[]>> images = ArgumentCaptor.forClass(List.class);
        verify(returnService).create(eq(5L), eq(7L), eq("Sản phẩm bị lỗi"), eq("Bị vỡ"), images.capture(),
                eq(List.of(new ReturnItemDraft(101L, 1))));
        assertEquals(2, images.getValue().size());
        assertEquals(4, images.getValue().get(0).length);
    }

    @Test
    void create_malformedItems_is400ValidationError_andNeverReachesTheService() throws Exception {
        for (String bad : new String[] {"not json", "[]", "[{\"orderItemId\":1}]", "[{\"orderItemId\":1,\"quantity\":2,\"price\":1}]"}) {
            mvc.perform(createRequest().param("items", bad).requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(returnService);
    }

    @Test
    void create_serviceRefusals_areMappedToTheStandardErrors() throws Exception {
        when(returnService.create(anyLong(), anyLong(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleViolationException("Đơn hàng chưa giao"))
                .thenThrow(new ForbiddenException("Không phải đơn của bạn"));

        mvc.perform(createRequest().requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        mvc.perform(createRequest().requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void create_withoutImagesOrOrderId_isLeftToTheService_whichRefuses() throws Exception {
        when(returnService.create(anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new com.example.backend.exception.BadRequestException("Vui lòng đính kèm ảnh"));

        mvc.perform(multipart("/api/v1/returns").param("reason", "x").param("items", ITEMS)
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---- GET /returns/my ----

    @Test
    void myReturns_returnsThePagedEnvelope_forTheTokenUser() throws Exception {
        when(returnService.listMyReturns(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto(1L, ReturnStatus.PENDING), dto(2L, ReturnStatus.REFUNDED)),
                        PageRequest.of(1, 2), 5));

        mvc.perform(get("/api/v1/returns/my").param("page", "1").param("size", "2").param("userId", "6")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].status").value("REFUNDED"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(5))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(returnService).listMyReturns(eq(5L), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(2, pageable.getValue().getPageSize());
    }

    @Test
    void myReturns_sizeAbove100_isClamped_andDefaultsToTwenty() throws Exception {
        when(returnService.listMyReturns(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mvc.perform(get("/api/v1/returns/my").param("size", "1000").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/returns/my").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(returnService, org.mockito.Mockito.times(2)).listMyReturns(eq(5L), pageable.capture());
        assertEquals(100, pageable.getAllValues().get(0).getPageSize());
        assertEquals(20, pageable.getAllValues().get(1).getPageSize());
    }

    @Test
    void myReturns_sortOutsideTheWhitelist_is400ValidationError() throws Exception {
        mvc.perform(get("/api/v1/returns/my").param("sort", "adminNote,asc")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(returnService);
    }

    // ---- GET /returns/{id} ----

    @Test
    void getReturn_ofAnotherCustomer_is403Forbidden() throws Exception {
        when(returnService.getReturn(eq(3L), any(ReturnViewer.class))).thenThrow(new ForbiddenException("no"));

        mvc.perform(get("/api/v1/returns/3").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void getReturn_viewerIsBuiltFromTheTokenPermissions() throws Exception {
        when(returnService.getReturn(eq(3L), any(ReturnViewer.class))).thenReturn(dto(3L, ReturnStatus.PENDING));

        mvc.perform(get("/api/v1/returns/3").requestAttr(AuthInterceptor.REQUEST_ATTR,
                        staff(Permissions.RETURN_VIEW, Permissions.RETURN_PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.allowedNextStatuses[0]").value("APPROVED"));
        mvc.perform(get("/api/v1/returns/3").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isOk());

        ArgumentCaptor<ReturnViewer> viewer = ArgumentCaptor.forClass(ReturnViewer.class);
        verify(returnService, org.mockito.Mockito.times(2)).getReturn(eq(3L), viewer.capture());
        assertEquals(new ReturnViewer(99L, true, true), viewer.getAllValues().get(0));
        assertEquals(new ReturnViewer(5L, false, false), viewer.getAllValues().get(1));
    }

    // ---- GET /admin/returns ----

    @Test
    void adminList_permissionMatrix() throws Exception {
        when(returnService.listAdminReturns(any(), any(Pageable.class), any(ReturnViewer.class)))
                .thenReturn(new PageImpl<>(List.of(dto(1L, ReturnStatus.PENDING)), PageRequest.of(0, 20), 1));

        // denied: a customer, and staff who can process but not view
        mvc.perform(get("/api/v1/admin/returns").requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/admin/returns").requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_PROCESS)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(returnService);

        // allowed: return:view
        mvc.perform(get("/api/v1/admin/returns").requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void adminList_passesTheStatusFilter_andRejectsAnUnknownOne() throws Exception {
        when(returnService.listAdminReturns(any(), any(Pageable.class), any(ReturnViewer.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/v1/admin/returns").param("status", "info_requested")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_VIEW)))
                .andExpect(status().isOk());
        verify(returnService).listAdminReturns(eq(ReturnStatus.INFO_REQUESTED), any(Pageable.class),
                eq(new ReturnViewer(99L, true, false)));

        mvc.perform(get("/api/v1/admin/returns").param("status", "DONE")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adminList_noStatusMeansAll() throws Exception {
        when(returnService.listAdminReturns(any(), any(Pageable.class), any(ReturnViewer.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/v1/admin/returns").requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_VIEW)))
                .andExpect(status().isOk());

        verify(returnService).listAdminReturns(eq(null), any(Pageable.class), any(ReturnViewer.class));
    }

    // ---- PATCH /admin/returns/{id}/status ----

    @Test
    void updateStatus_permissionMatrix() throws Exception {
        when(returnService.updateStatus(anyLong(), any(ReturnStatus.class), any(), anyLong()))
                .thenReturn(dto(3L, ReturnStatus.APPROVED));
        String body = "{\"newStatus\":\"APPROVED\"}";

        // denied: a customer, and staff who can only view
        mvc.perform(patch("/api/v1/admin/returns/3/status").contentType(JSON).content(body)
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(patch("/api/v1/admin/returns/3/status").contentType(JSON).content(body)
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_VIEW)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(returnService);

        // allowed: return:process; the actor is the token user
        mvc.perform(patch("/api/v1/admin/returns/3/status").contentType(JSON).content(body)
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        verify(returnService).updateStatus(3L, ReturnStatus.APPROVED, null, 99L);
    }

    @Test
    void updateStatus_adminNoteIsRequired_forRejectedAndInfoRequested() throws Exception {
        for (String target : new String[] {"REJECTED", "INFO_REQUESTED"}) {
            for (String bodyNote : new String[] {"", ",\"adminNote\":null", ",\"adminNote\":\"\"", ",\"adminNote\":\"   \""}) {
                mvc.perform(patch("/api/v1/admin/returns/3/status").contentType(JSON)
                                .content("{\"newStatus\":\"" + target + "\"" + bodyNote + "}")
                                .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_PROCESS)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
            }
        }
        verify(returnService, never()).updateStatus(anyLong(), any(ReturnStatus.class), any(), anyLong());
    }

    @Test
    void updateStatus_withANote_isAccepted_forRejectedAndInfoRequested() throws Exception {
        when(returnService.updateStatus(anyLong(), any(ReturnStatus.class), any(), anyLong()))
                .thenReturn(dto(3L, ReturnStatus.REJECTED));

        mvc.perform(patch("/api/v1/admin/returns/3/status").contentType(JSON)
                        .content("{\"newStatus\":\"rejected\",\"adminNote\":\"Quá hạn 7 ngày\"}")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_PROCESS)))
                .andExpect(status().isOk());
        verify(returnService).updateStatus(3L, ReturnStatus.REJECTED, "Quá hạn 7 ngày", 99L);
    }

    @Test
    void updateStatus_unknownMissingOrTooLong_is400ValidationError() throws Exception {
        for (String body : new String[] {"{\"newStatus\":\"DONE\"}", "{}", "{\"newStatus\":\"\"}",
                "{\"newStatus\":\"APPROVED\",\"adminNote\":\"" + "x".repeat(2001) + "\"}"}) {
            mvc.perform(patch("/api/v1/admin/returns/3/status").contentType(JSON).content(body)
                            .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_PROCESS)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(returnService);
    }

    @Test
    void updateStatus_invalidTransition_is400BusinessRuleViolation() throws Exception {
        when(returnService.updateStatus(anyLong(), any(ReturnStatus.class), any(), anyLong()))
                .thenThrow(new BusinessRuleViolationException("Không thể chuyển yêu cầu đổi/trả từ PENDING sang REFUNDED."));

        mvc.perform(patch("/api/v1/admin/returns/3/status").contentType(JSON).content("{\"newStatus\":\"REFUNDED\"}")
                        .requestAttr(AuthInterceptor.REQUEST_ATTR, staff(Permissions.RETURN_PROCESS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }
}
