package com.example.backend.controller;

import com.example.backend.entity.Order;
import com.example.backend.entity.Payment;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.PaymentService;
import com.example.backend.service.payment.PaymentInitiationResult;
import com.example.backend.service.payment.WebhookOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B06: /api/v1/payments through standalone MockMvc (real controller, real GlobalExceptionHandler, mocked service). The request
 * attribute stands in for what AuthInterceptor sets from a verified token. The webhook is called WITHOUT it.
 */
class PaymentV1ControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final long CALLER = 42L;

    private PaymentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PaymentService.class);
        PaymentV1Controller controller = new PaymentV1Controller();
        ReflectionTestUtils.setField(controller, "paymentService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();

        when(service.isMockEnabled()).thenReturn(false);
        when(service.getByOrderId(7L)).thenReturn(payment());
        when(service.getPaymentFor(anyLong(), anyLong(), anyBoolean())).thenReturn(payment());
    }

    private static Payment payment() {
        Order order = new Order();
        order.setId(7L);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod("VNPAY");
        payment.setProvider("VNPAY");
        payment.setAmount(new BigDecimal("255000.00"));
        payment.setStatus(Payment.Status.PENDING);
        payment.setRawResponse("SECRET-GATEWAY-PAYLOAD");
        return payment;
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String... permissions) {
        return builder.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(CALLER, List.of("CUSTOMER"), List.of(permissions)));
    }

    // ---- webhook: public ---------------------------------------------------------------------------------------------

    @Test
    void webhookEndpoint_requiresNoAuthentication() throws Exception {
        when(service.handleWebhook(eq("VNPAY"), anyMap())).thenReturn(WebhookOutcome.PROCESSED);

        mvc.perform(get("/api/v1/payments/webhook/VNPAY").param("vnp_TxnRef", "7-REF").param("vnp_SecureHash", "abc")) // no token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"))
                .andExpect(jsonPath("$.data").doesNotExist()); // the one non-envelope answer

        mvc.perform(post("/api/v1/payments/webhook/VNPAY").param("vnp_TxnRef", "7-REF")).andExpect(status().isOk());
    }

    @Test
    void webhook_passesTheQueryParametersToTheService() throws Exception {
        when(service.handleWebhook(anyString(), anyMap())).thenReturn(WebhookOutcome.PROCESSED);

        mvc.perform(get("/api/v1/payments/webhook/VNPAY").param("vnp_TxnRef", "7-REF").param("vnp_Amount", "25500000"));

        verify(service).handleWebhook("VNPAY", Map.of("vnp_TxnRef", "7-REF", "vnp_Amount", "25500000"));
    }

    @Test
    void webhook_answersTheGatewayCodes() throws Exception {
        Object[][] cases = {
                {WebhookOutcome.ALREADY_PROCESSED, "02"}, {WebhookOutcome.INVALID_SIGNATURE, "97"},
                {WebhookOutcome.PAYMENT_NOT_FOUND, "01"}, {WebhookOutcome.AMOUNT_MISMATCH, "04"}};
        for (Object[] c : cases) {
            when(service.handleWebhook(anyString(), anyMap())).thenReturn((WebhookOutcome) c[0]);

            mvc.perform(get("/api/v1/payments/webhook/VNPAY")).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value(c[1]));
        }
    }

    @Test
    void webhook_whenTheOrderTransitionFails_answersARetryableCode() throws Exception {
        when(service.handleWebhook(anyString(), anyMap())).thenThrow(new BusinessRuleViolationException("internal detail that must not leak"));

        mvc.perform(get("/api/v1/payments/webhook/VNPAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("99"))
                .andExpect(jsonPath("$.Message").value("Unknown error"));
    }

    @Test
    void webhook_unknownProvider_isTheStandard404() throws Exception {
        when(service.handleWebhook(eq("PAYPAL"), anyMap())).thenThrow(new ResourceNotFoundException("Không tìm thấy cổng thanh toán."));

        mvc.perform(get("/api/v1/payments/webhook/PAYPAL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ---- authenticated endpoints -----------------------------------------------------------------------------------

    @Test
    void initiate_withoutToken_is401() throws Exception {
        mvc.perform(post("/api/v1/payments/7/initiate")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void initiate_returnsTheRedirect_inTheEnvelope() throws Exception {
        when(service.initiatePayment(eq(7L), eq(CALLER), any(), anyString()))
                .thenReturn(new PaymentInitiationResult("https://pay.example/go", "7-REF"));

        mvc.perform(as(post("/api/v1/payments/7/initiate").contentType(JSON).content("{\"returnUrl\":\"http://localhost:5173/payment/return\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redirectUrl").value("https://pay.example/go"))
                .andExpect(jsonPath("$.data.provider").value("VNPAY"));
    }

    @Test
    void initiate_forAnotherUsersOrder_is403() throws Exception {
        when(service.initiatePayment(anyLong(), anyLong(), any(), anyString())).thenThrow(new ForbiddenException("Bạn không có quyền truy cập đơn hàng này."));

        mvc.perform(as(post("/api/v1/payments/7/initiate"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void get_neverReturnsTheRawGatewayResponse() throws Exception {
        mvc.perform(as(get("/api/v1/payments/7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value(255000.00))
                .andExpect(jsonPath("$.data.rawResponse").doesNotExist())
                .andExpect(jsonPath("$.data.raw_response").doesNotExist())
                .andExpect(jsonPath("$.data.mockEnabled").value(false));
    }

    @Test
    void get_staffWithPaymentView_canReadAnyOrder() throws Exception {
        mvc.perform(as(get("/api/v1/payments/7"), Permissions.PAYMENT_VIEW)).andExpect(status().isOk());

        verify(service).getPaymentFor(7L, CALLER, true);
        mvc.perform(as(get("/api/v1/payments/7"))).andExpect(status().isOk());
        verify(service).getPaymentFor(7L, CALLER, false);
    }

    @Test
    void updateMethod_isValidated() throws Exception {
        mvc.perform(as(put("/api/v1/payments/7/method").contentType(JSON).content("{\"method\":\"  \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.method").exists());
    }

    @Test
    void mockPay_rejectsAnUnknownOutcome() throws Exception {
        mvc.perform(as(post("/api/v1/payments/7/mock-pay").contentType(JSON).content("{\"outcome\":\"MAYBE\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
