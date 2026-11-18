package com.example.backend.controller;

import com.example.backend.entity.User;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.CustomerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerProfileV1ControllerTest {

    private static final long CALLER = 42L;
    private CustomerProfileService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CustomerProfileService.class);
        CustomerProfileV1Controller controller = new CustomerProfileV1Controller();
        ReflectionTestUtils.setField(controller, "profileService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.get(CALLER)).thenReturn(user());
        when(service.update(eq(CALLER), any())).thenReturn(user());
    }

    private static User user() {
        User user = new User();
        user.setId(CALLER);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPhone("0901234567");
        return user;
    }

    private static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder request) {
        return request.requestAttr(AuthInterceptor.REQUEST_ATTR,
                new CurrentUser(CALLER, List.of("CUSTOMER"), List.of()));
    }

    @Test
    void profileEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"email\":\"alice@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileIsAlwaysReadAndWrittenForTheTokenOwner() throws Exception {
        mvc.perform(asCustomer(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"phone\":\"0901234567\",\"userId\":99}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(CALLER))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        verify(service).update(eq(CALLER), any());
    }
}
