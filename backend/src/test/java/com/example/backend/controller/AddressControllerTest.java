package com.example.backend.controller;

import com.example.backend.dto.AddressRequest;
import com.example.backend.entity.Address;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B08 address endpoints: the caller comes from the token, errors use the /api/v1 shape (standalone MockMvc, mocked service). */
class AddressControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final long CALLER = 42L;
    private static final String BODY = "{\"recipientName\":\"Nguyễn Văn A\",\"phone\":\"0901 234 567\",\"line1\":\"1 Lê Lợi\","
            + "\"ward\":\"Bến Nghé\",\"district\":\"Quận 1\",\"province\":\"TP. Hồ Chí Minh\",\"isDefault\":true}";

    private AddressService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AddressService.class);
        AddressV1Controller controller = new AddressV1Controller();
        ReflectionTestUtils.setField(controller, "addressService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();

        when(service.list(CALLER)).thenReturn(List.of(address(1L, true), address(2L, false)));
        when(service.create(anyLong(), any(AddressRequest.class))).thenReturn(address(3L, false));
        when(service.update(anyLong(), anyLong(), any(AddressRequest.class))).thenReturn(address(1L, true));
        when(service.setDefault(anyLong(), anyLong())).thenReturn(address(2L, true));
    }

    private static Address address(long id, boolean isDefault) {
        Address a = new Address();
        a.setId(id);
        a.setUserId(CALLER);
        a.setRecipientName("Nguyễn Văn A");
        a.setPhone("0901234567");
        a.setLine1("1 Lê Lợi");
        a.setWard("Bến Nghé");
        a.setDistrict("Quận 1");
        a.setProvince("TP. Hồ Chí Minh");
        a.setDefault(isDefault);
        return a;
    }

    private static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder b) {
        return b.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(CALLER, List.of("CUSTOMER"), List.of()));
    }

    @Test
    void everyEndpoint_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/users/me/addresses")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/users/me/addresses").contentType(JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/addresses/1").contentType(JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/users/me/addresses/1/default")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/users/me/addresses/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_returnsTheCallersBook_inTheEnvelope_withoutAUserId() throws Exception {
        mvc.perform(asCustomer(get("/api/v1/users/me/addresses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].isDefault").value(false))
                .andExpect(jsonPath("$.data[0].line1").value("1 Lê Lợi"))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void create_returns201_andUsesTheTokenUser() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/users/me/addresses").contentType(JSON).content(BODY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(3));

        ArgumentCaptor<AddressRequest> body = ArgumentCaptor.forClass(AddressRequest.class);
        verify(service).create(org.mockito.ArgumentMatchers.eq(CALLER), body.capture());
        assertEquals("0901 234 567", body.getValue().phone());
        assertEquals(Boolean.TRUE, body.getValue().isDefault(), "JSON property isDefault is bound");
    }

    @Test
    void create_ignoresAnyUserIdSentByTheClient() throws Exception {
        String sneaky = BODY.replace("{", "{\"userId\":1,");

        mvc.perform(asCustomer(post("/api/v1/users/me/addresses").contentType(JSON).content(sneaky)))
                .andExpect(status().isCreated());

        verify(service).create(org.mockito.ArgumentMatchers.eq(CALLER), any(AddressRequest.class));
    }

    @Test
    void create_invalidPhone_is400WithTheFieldName() throws Exception {
        String bad = BODY.replace("0901 234 567", "12345");

        mvc.perform(asCustomer(post("/api/v1/users/me/addresses").contentType(JSON).content(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.phone").exists());
        verify(service, never()).create(anyLong(), any(AddressRequest.class));
    }

    @Test
    void create_acceptsCommonVietnamesePhoneFormats() throws Exception {
        for (String phone : new String[] {"0901234567", "+84901234567", "84901234567", "090-123-4567", "0901.234.567", "02838291234"}) {
            mvc.perform(asCustomer(post("/api/v1/users/me/addresses").contentType(JSON).content(BODY.replace("0901 234 567", phone))))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void create_rejectsNonVietnamesePhones() throws Exception {
        for (String phone : new String[] {"+14155552671", "abcdefghij", "0123", "090123456789012"}) {
            mvc.perform(asCustomer(post("/api/v1/users/me/addresses").contentType(JSON).content(BODY.replace("0901 234 567", phone))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void create_missingRequiredFields_is400() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/users/me/addresses").contentType(JSON).content("{\"recipientName\":\" \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.recipientName").exists())
                .andExpect(jsonPath("$.error.fields.line1").exists())
                .andExpect(jsonPath("$.error.fields.province").exists());
    }

    @Test
    void create_eleventh_is400BusinessRuleViolation() throws Exception {
        when(service.create(anyLong(), any(AddressRequest.class))).thenThrow(new BusinessRuleViolationException("Sổ địa chỉ chỉ lưu tối đa 10 địa chỉ."));

        mvc.perform(asCustomer(post("/api/v1/users/me/addresses").contentType(JSON).content(BODY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void update_andSetDefault_useTheTokenUserAndThePathId() throws Exception {
        mvc.perform(asCustomer(put("/api/v1/users/me/addresses/1").contentType(JSON).content(BODY))).andExpect(status().isOk());
        mvc.perform(asCustomer(patch("/api/v1/users/me/addresses/2/default")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isDefault").value(true));

        verify(service).update(org.mockito.ArgumentMatchers.eq(CALLER), org.mockito.ArgumentMatchers.eq(1L), any(AddressRequest.class));
        verify(service).setDefault(CALLER, 2L);
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(asCustomer(delete("/api/v1/users/me/addresses/1"))).andExpect(status().isNoContent());

        verify(service).delete(CALLER, 1L);
    }

    @Test
    void someoneElsesAddress_is403_forUpdateDeleteAndSetDefault() throws Exception {
        when(service.update(anyLong(), anyLong(), any(AddressRequest.class))).thenThrow(new ForbiddenException("Bạn không có quyền truy cập địa chỉ này."));
        when(service.setDefault(anyLong(), anyLong())).thenThrow(new ForbiddenException("Bạn không có quyền truy cập địa chỉ này."));
        org.mockito.Mockito.doThrow(new ForbiddenException("Bạn không có quyền truy cập địa chỉ này.")).when(service).delete(anyLong(), anyLong());

        mvc.perform(asCustomer(put("/api/v1/users/me/addresses/9").contentType(JSON).content(BODY)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(asCustomer(delete("/api/v1/users/me/addresses/9"))).andExpect(status().isForbidden());
        mvc.perform(asCustomer(patch("/api/v1/users/me/addresses/9/default"))).andExpect(status().isForbidden());
    }

    @Test
    void unknownAddress_is404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Không tìm thấy địa chỉ #77")).when(service).delete(CALLER, 77L);

        mvc.perform(asCustomer(delete("/api/v1/users/me/addresses/77")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
