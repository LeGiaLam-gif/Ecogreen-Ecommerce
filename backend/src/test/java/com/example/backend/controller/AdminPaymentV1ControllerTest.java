package com.example.backend.controller;

import com.example.backend.entity.Order;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Refund;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.PaymentService;
import com.example.backend.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B06: the staff endpoints - the permission matrix (401 / 403 / success) with the real AuthGuard, and error mapping. */
class AdminPaymentV1ControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final long STAFF = 5L;

    private PaymentService paymentService;
    private RefundService refundService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        refundService = mock(RefundService.class);
        AdminPaymentV1Controller controller = new AdminPaymentV1Controller();
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
        ReflectionTestUtils.setField(controller, "refundService", refundService);
        ReflectionTestUtils.setField(controller, "authGuard", new AuthGuard());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();

        Order order = new Order();
        order.setId(7L);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod("VIETQR");
        payment.setAmount(new BigDecimal("255000.00"));
        payment.setStatus(Payment.Status.SUCCESS);
        when(paymentService.confirmManually(eq(7L), eq(STAFF), any())).thenReturn(payment);

        Refund refund = new Refund();
        refund.setOrder(order);
        refund.setAmount(new BigDecimal("255000.00"));
        refund.setStatus(Refund.Status.SUCCESS);
        when(refundService.completeRefund(eq(7L), eq(STAFF), any())).thenReturn(refund);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String... permissions) {
        return builder.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(STAFF, List.of("MANAGER"), List.of(permissions)));
    }

    @Test
    void confirm_withoutPermission_forbidden() throws Exception {
        mvc.perform(as(post("/api/v1/admin/payments/7/confirm"), Permissions.PAYMENT_VIEW, Permissions.PAYMENT_REFUND)) // denied caller
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/admin/payments/7/confirm")).andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void confirm_withPermission_succeeds_andPassesTheActor() throws Exception {
        mvc.perform(as(post("/api/v1/admin/payments/7/confirm").contentType(JSON).content("{\"note\":\"Đã nhận tiền\"}"), Permissions.PAYMENT_CONFIRM)) // allowed caller
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.rawResponse").doesNotExist());

        verify(paymentService).confirmManually(7L, STAFF, "Đã nhận tiền");
    }

    @Test
    void confirm_codOrGatewayPayment_is400BusinessRule() throws Exception {
        when(paymentService.confirmManually(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessRuleViolationException("Chỉ xác nhận thủ công cho thanh toán chuyển khoản VietQR hoặc MoMo."));

        mvc.perform(as(post("/api/v1/admin/payments/7/confirm"), Permissions.PAYMENT_CONFIRM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void completeRefund_permissionMatrix() throws Exception {
        mvc.perform(as(post("/api/v1/admin/payments/7/refund/complete"), Permissions.PAYMENT_CONFIRM)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/payments/7/refund/complete")).andExpect(status().isUnauthorized());
        verifyNoInteractions(refundService);

        mvc.perform(as(post("/api/v1/admin/payments/7/refund/complete").contentType(JSON).content("{\"providerTransactionId\":\"FT1\"}"),
                        Permissions.PAYMENT_REFUND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        verify(refundService).completeRefund(7L, STAFF, "FT1");
    }

    @Test
    void summaries_needPaymentView_andAreCappedAt100Ids() throws Exception {
        mvc.perform(as(get("/api/v1/admin/payments").param("orderIds", "1,2"), Permissions.PAYMENT_CONFIRM)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/payments").param("orderIds", "1,2")).andExpect(status().isUnauthorized());

        mvc.perform(as(get("/api/v1/admin/payments").param("orderIds", "1,2"), Permissions.PAYMENT_VIEW)).andExpect(status().isOk());
        verify(paymentService).listSummaries(List.of(1L, 2L));

        String tooMany = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
        mvc.perform(as(get("/api/v1/admin/payments").param("orderIds", tooMany), Permissions.PAYMENT_VIEW))
                .andExpect(status().isBadRequest());
        verify(paymentService, org.mockito.Mockito.times(1)).listSummaries(any()); // only the call with 2 ids reached the service
    }
}
