package com.example.backend.service;

import com.example.backend.dto.AdminPaymentSummaryResponse;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Refund;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.RefundRepository;
import com.example.backend.service.payment.MockGatewayClient;
import com.example.backend.service.payment.PaymentGatewayClient;
import com.example.backend.service.payment.PaymentGatewayRegistry;
import com.example.backend.service.payment.PaymentInitiationResult;
import com.example.backend.service.payment.WebhookOutcome;
import com.example.backend.service.payment.WebhookVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B06: webhook order of steps, initiate rules, manual confirmation, method validation. Mockito only - no database, no HTTP. */
class PaymentServiceTest {

    private static final long OWNER = 42L;
    private static final long STRANGER = 99L;
    private static final String RETURN_BASE = "http://localhost:5173/payment/return";

    private PaymentRepository payments;
    private RefundRepository refunds;
    private OrderService orderService;
    private PaymentGatewayClient vnpay;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        refunds = mock(RefundRepository.class);
        orderService = mock(OrderService.class);
        vnpay = mock(PaymentGatewayClient.class);
        when(vnpay.providerCode()).thenReturn("VNPAY");
        when(vnpay.isAvailable()).thenReturn(true);
        service = new PaymentService();
        ReflectionTestUtils.setField(service, "paymentRepository", payments);
        ReflectionTestUtils.setField(service, "refundRepository", refunds);
        ReflectionTestUtils.setField(service, "orderService", orderService);
        ReflectionTestUtils.setField(service, "gateways", new PaymentGatewayRegistry(List.of(vnpay)));
        ReflectionTestUtils.setField(service, "returnUrlBase", RETURN_BASE);
        ReflectionTestUtils.setField(service, "mockEnabled", false);
    }

    private Payment payment(OrderStatus orderStatus, Payment.Status status, String method) {
        User owner = new User();
        owner.setId(OWNER);
        Order order = new Order();
        order.setId(7L);
        order.setUser(owner);
        order.setStatus(orderStatus);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(method);
        payment.setAmount(new BigDecimal("255000.00"));
        payment.setStatus(status);
        when(payments.findByOrderId(7L)).thenReturn(Optional.of(payment));
        when(payments.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(payment));
        return payment;
    }

    private Payment gatewayAttempt(Payment.Status status) {
        Payment payment = payment(OrderStatus.PENDING_PAYMENT, status, "VNPAY");
        payment.setProvider("VNPAY");
        payment.setTransactionId("7-REF");
        when(payments.findByProviderAndTransactionIdForUpdate("VNPAY", "7-REF")).thenReturn(Optional.of(payment));
        return payment;
    }

    private void callbackSays(boolean valid, boolean success, String amount) {
        when(vnpay.verifyAndParse(any())).thenReturn(valid
                ? new WebhookVerificationResult(true, success, "7-REF", amount == null ? null : new BigDecimal(amount), success ? "00" : "24", "raw")
                : WebhookVerificationResult.invalid());
    }

    // ---- handleWebhook ----------------------------------------------------------------------------------------------

    @Test
    void handleWebhook_invalidSignature_changesNothing() {
        callbackSays(false, false, null);

        WebhookOutcome outcome = service.handleWebhook("VNPAY", Map.of("vnp_TxnRef", "7-REF"));

        assertEquals(WebhookOutcome.INVALID_SIGNATURE, outcome);
        verifyNoInteractions(payments, orderService, refunds);
    }

    @Test
    void handleWebhook_success_marksPaymentAndOrderPaid() {
        Payment payment = gatewayAttempt(Payment.Status.PENDING);
        callbackSays(true, true, "255000");

        WebhookOutcome outcome = service.handleWebhook("vnpay", Map.of());

        assertEquals(WebhookOutcome.PROCESSED, outcome);
        assertEquals(Payment.Status.SUCCESS, payment.getStatus());
        assertNotNull(payment.getPaidAt());
        assertEquals("raw", payment.getRawResponse());
        verify(payments).save(payment);
        verify(orderService).transitionStatus(7L, OrderStatus.PAID, null, "Auto by payment webhook");
        verify(orderService, times(1)).transitionStatus(anyLong(), any(), any(), anyString());
    }

    @Test
    void handleWebhook_loadsThePaymentWithTheRowLock() {
        gatewayAttempt(Payment.Status.PENDING);
        callbackSays(true, true, "255000");

        service.handleWebhook("VNPAY", Map.of());

        verify(payments).findByProviderAndTransactionIdForUpdate("VNPAY", "7-REF");
        verify(payments, never()).findByOrderId(anyLong());
    }

    @Test
    void handleWebhook_duplicateDelivery_callsTransitionOnce() {
        gatewayAttempt(Payment.Status.PENDING);
        callbackSays(true, true, "255000");

        WebhookOutcome first = service.handleWebhook("VNPAY", Map.of());
        WebhookOutcome second = service.handleWebhook("VNPAY", Map.of()); // same payment object, now SUCCESS

        assertEquals(WebhookOutcome.PROCESSED, first);
        assertEquals(WebhookOutcome.ALREADY_PROCESSED, second);
        verify(orderService, times(1)).transitionStatus(anyLong(), any(), any(), anyString());
    }

    @Test
    void handleWebhook_alreadyFailedOrRefunded_isIdempotent() {
        for (Payment.Status settled : new Payment.Status[] {Payment.Status.FAILED, Payment.Status.CANCELLED, Payment.Status.REFUNDED}) {
            gatewayAttempt(settled);
            callbackSays(true, true, "255000");

            assertEquals(WebhookOutcome.ALREADY_PROCESSED, service.handleWebhook("VNPAY", Map.of()));
        }
        verifyNoInteractions(orderService);
    }

    @Test
    void handleWebhook_failure_cancelsOrder() {
        Payment payment = gatewayAttempt(Payment.Status.PENDING);
        callbackSays(true, false, "255000");

        WebhookOutcome outcome = service.handleWebhook("VNPAY", Map.of());

        assertEquals(WebhookOutcome.PROCESSED, outcome);
        assertEquals(Payment.Status.FAILED, payment.getStatus());
        assertNull(payment.getPaidAt());
        verify(orderService).transitionStatus(7L, OrderStatus.CANCELLED, null, "Payment failed");
    }

    @Test
    void handleWebhook_amountMismatch_rejected() {
        Payment payment = gatewayAttempt(Payment.Status.PENDING);
        for (String amount : new String[] {"254999", "255001", "1", null}) {
            callbackSays(true, true, amount);

            assertEquals(WebhookOutcome.AMOUNT_MISMATCH, service.handleWebhook("VNPAY", Map.of()), String.valueOf(amount));
        }
        assertEquals(Payment.Status.PENDING, payment.getStatus());
        verify(payments, never()).save(any());
        verifyNoInteractions(orderService);
    }

    @Test
    void handleWebhook_amountComparison_ignoresScale() {
        gatewayAttempt(Payment.Status.PENDING);
        callbackSays(true, true, "255000.0000"); // same number, different scale

        assertEquals(WebhookOutcome.PROCESSED, service.handleWebhook("VNPAY", Map.of()));
    }

    @Test
    void handleWebhook_unknownReference_changesNothing() {
        callbackSays(true, true, "255000");
        when(payments.findByProviderAndTransactionIdForUpdate(anyString(), anyString())).thenReturn(Optional.empty());

        assertEquals(WebhookOutcome.PAYMENT_NOT_FOUND, service.handleWebhook("VNPAY", Map.of()));
        verifyNoInteractions(orderService);
    }

    @Test
    void handleWebhook_orderAlreadyCancelled_rollsBack() {
        gatewayAttempt(Payment.Status.PENDING);
        callbackSays(true, true, "255000");
        doThrow(new BusinessRuleViolationException("Không thể chuyển đơn hàng từ CANCELLED sang PAID."))
                .when(orderService).transitionStatus(7L, OrderStatus.PAID, null, "Auto by payment webhook");

        // The exception must reach the caller: it is what rolls the @Transactional method back and makes the controller
        // answer a retryable code. It must not be swallowed here.
        assertThrows(BusinessRuleViolationException.class, () -> service.handleWebhook("VNPAY", Map.of()));
    }

    @Test
    void handleWebhook_unknownProvider_isNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.handleWebhook("PAYPAL", Map.of()));
        verifyNoInteractions(payments, orderService);
    }

    // ---- initiatePayment --------------------------------------------------------------------------------------------

    private void gatewayReturns(String ref) {
        when(vnpay.initiate(any(Payment.class), anyString(), anyString()))
                .thenReturn(new PaymentInitiationResult("https://pay.example/redirect", ref));
    }

    @Test
    void initiate_owner_storesAFreshReference_andReturnsTheRedirect() {
        Payment payment = payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "VNPAY");
        gatewayReturns("7-NEW");

        PaymentInitiationResult result = service.initiatePayment(7L, OWNER, null, "203.0.113.9");

        assertEquals("https://pay.example/redirect", result.redirectUrl());
        assertEquals("7-NEW", payment.getTransactionId());
        assertEquals("VNPAY", payment.getProvider());
        assertEquals(Payment.Status.PENDING, payment.getStatus());
        verify(payments).save(payment);
    }

    @Test
    void initiate_nonOwner_forbidden() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "VNPAY");

        assertThrows(ForbiddenException.class, () -> service.initiatePayment(7L, STRANGER, null, "127.0.0.1"));
        verify(vnpay, never()).initiate(any(), any(), any());
    }

    @Test
    void initiate_nonPendingOrder_rejected() {
        for (OrderStatus status : new OrderStatus[] {OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.PROCESSING, OrderStatus.DELIVERED}) {
            payment(status, Payment.Status.PENDING, "VNPAY");

            assertThrows(BusinessRuleViolationException.class, () -> service.initiatePayment(7L, OWNER, null, "127.0.0.1"), status.name());
        }
        verify(vnpay, never()).initiate(any(), any(), any());
    }

    @Test
    void initiate_alreadyPaid_orNotAGatewayMethod_rejected() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.SUCCESS, "VNPAY");
        assertThrows(BusinessRuleViolationException.class, () -> service.initiatePayment(7L, OWNER, null, "127.0.0.1"));

        for (String method : new String[] {"COD", "VIETQR", "MOMO"}) {
            payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, method);
            assertThrows(BusinessRuleViolationException.class, () -> service.initiatePayment(7L, OWNER, null, "127.0.0.1"), method);
        }
        verify(vnpay, never()).initiate(any(), any(), any());
    }

    @Test
    void initiate_foreignReturnUrl_ignored() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "VNPAY");
        gatewayReturns("7-NEW");
        ArgumentCaptor<String> used = ArgumentCaptor.forClass(String.class);

        for (String evil : new String[] {"https://evil.example/steal", "http://localhost:5173/other", RETURN_BASE + ".evil.example", "//evil.example"}) {
            service.initiatePayment(7L, OWNER, evil, "127.0.0.1");
        }
        verify(vnpay, times(4)).initiate(any(Payment.class), used.capture(), anyString());
        for (String url : used.getAllValues()) {
            assertEquals(RETURN_BASE + "?orderId=7", url);
        }
    }

    @Test
    void initiate_returnUrlUnderTheConfiguredBase_isHonoured() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "VNPAY");
        gatewayReturns("7-NEW");
        ArgumentCaptor<String> used = ArgumentCaptor.forClass(String.class);

        service.initiatePayment(7L, OWNER, RETURN_BASE + "?from=cart", "127.0.0.1");

        verify(vnpay).initiate(any(Payment.class), used.capture(), anyString());
        assertEquals(RETURN_BASE + "?from=cart&orderId=7", used.getValue());
    }

    @Test
    void initiate_vnpayNotConfigured_withMockDisabled_usesVnpayWhichFailsClosed() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "VNPAY");
        when(vnpay.isAvailable()).thenReturn(false);
        when(vnpay.initiate(any(), anyString(), anyString())).thenThrow(new BusinessRuleViolationException("Cổng thanh toán VNPAY chưa được cấu hình."));

        assertThrows(BusinessRuleViolationException.class, () -> service.initiatePayment(7L, OWNER, null, "127.0.0.1"));
        verify(payments, never()).save(any());
    }

    @Test
    void initiate_vnpayNotConfigured_withMockEnabled_usesTheMockProvider() {
        MockGatewayClient mock = new MockGatewayClient();
        when(vnpay.isAvailable()).thenReturn(false);
        ReflectionTestUtils.setField(service, "gateways", new PaymentGatewayRegistry(List.of(vnpay, mock)));
        ReflectionTestUtils.setField(service, "mockEnabled", true);
        Payment payment = payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "VNPAY");

        service.initiatePayment(7L, OWNER, null, "127.0.0.1");

        assertEquals("MOCK", payment.getProvider());
        assertTrue(payment.getTransactionId().startsWith("MOCK-7-"));
        verify(vnpay, never()).initiate(any(), any(), any());
    }

    // ---- mock pay (TEMPORARY-COMPAT) -------------------------------------------------------------------------------

    @Test
    void mockPay_goesThroughTheSameWebhookPath_andPaysTheOrder() {
        MockGatewayClient mock = new MockGatewayClient();
        ReflectionTestUtils.setField(service, "gateways", new PaymentGatewayRegistry(List.of(vnpay, mock)));
        ReflectionTestUtils.setField(service, "mockEnabled", true);
        Payment payment = payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "VNPAY");
        payment.setProvider("MOCK");
        payment.setTransactionId("MOCK-7-abc");
        when(payments.findByProviderAndTransactionIdForUpdate("MOCK", "MOCK-7-abc")).thenReturn(Optional.of(payment));

        WebhookOutcome outcome = service.mockPay(7L, OWNER, true);

        assertEquals(WebhookOutcome.PROCESSED, outcome);
        assertEquals(Payment.Status.SUCCESS, payment.getStatus());
        verify(orderService).transitionStatus(7L, OrderStatus.PAID, null, "Auto by payment webhook");
    }

    @Test
    void mockPay_whenDisabled_orForAStranger_isRefused() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "VNPAY");
        assertThrows(BusinessRuleViolationException.class, () -> service.mockPay(7L, OWNER, true)); // mockEnabled=false

        ReflectionTestUtils.setField(service, "mockEnabled", true);
        assertThrows(ForbiddenException.class, () -> service.mockPay(7L, STRANGER, true));
        verifyNoInteractions(orderService);
    }

    @Test
    void mockCallbacks_cannotBeForged_withoutTheInProcessKey() {
        MockGatewayClient mock = new MockGatewayClient();
        Map<String, String> forged = Map.of("mock_ref", "MOCK-7-abc", "mock_amount", "255000.00", "mock_status", "00", "mock_sig", "0".repeat(64));

        assertFalse(mock.verifyAndParse(forged).signatureValid());
        assertTrue(mock.verifyAndParse(mock.signedCallback("MOCK-7-abc", new BigDecimal("255000.00"), true)).signatureValid());
        assertFalse(new MockGatewayClient().verifyAndParse(mock.signedCallback("MOCK-7-abc", new BigDecimal("255000.00"), true)).signatureValid());
    }

    // ---- confirmManually --------------------------------------------------------------------------------------------

    @Test
    void confirmManually_vietqrAndMomo_marksSuccess_andMovesTheOrderToPaidWithTheActor() {
        for (String method : new String[] {"VIETQR", "MOMO"}) {
            Payment payment = payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, method);

            service.confirmManually(7L, 5L, "Đã nhận 255.000");

            assertEquals(Payment.Status.SUCCESS, payment.getStatus(), method);
            assertNotNull(payment.getPaidAt());
            assertNull(payment.getProvider());
        }
        verify(orderService, times(2)).transitionStatus(7L, OrderStatus.PAID, 5L, "Đã nhận 255.000");
    }

    @Test
    void confirmManually_codOrGatewayMethod_rejected() {
        for (String method : new String[] {"COD", "VNPAY"}) {
            payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, method);

            assertThrows(BusinessRuleViolationException.class, () -> service.confirmManually(7L, 5L, null), method);
        }
        verifyNoInteractions(orderService);
    }

    @Test
    void confirmManually_alreadySettled_rejected() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.SUCCESS, "VIETQR");

        assertThrows(BusinessRuleViolationException.class, () -> service.confirmManually(7L, 5L, null));
        verifyNoInteractions(orderService);
    }

    // ---- methods ----------------------------------------------------------------------------------------------------

    @Test
    void updateMethod_unknownMethod_rejected() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "COD");

        for (String method : new String[] {null, "", "  ", "PAYPAL", "BITCOIN"}) {
            assertThrows(BadRequestException.class, () -> service.updateMethod(7L, OWNER, method), String.valueOf(method));
        }
        verify(payments, never()).save(any());
    }

    @Test
    void updateMethod_acceptsEverySupportedMethod_inAnyCase() {
        Payment payment = payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "COD");
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        for (String method : new String[] {"cod", " VietQR ", "momo", "VNPAY"}) {
            service.updateMethod(7L, OWNER, method);
        }
        assertEquals("VNPAY", payment.getPaymentMethod());
    }

    @Test
    void updateMethod_stranger_orPaid_orNotPending_refused() {
        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "COD");
        assertThrows(ForbiddenException.class, () -> service.updateMethod(7L, STRANGER, "MOMO"));

        payment(OrderStatus.PENDING_PAYMENT, Payment.Status.SUCCESS, "COD");
        assertThrows(BusinessRuleViolationException.class, () -> service.updateMethod(7L, OWNER, "MOMO"));

        payment(OrderStatus.CANCELLED, Payment.Status.PENDING, "COD");
        assertThrows(BusinessRuleViolationException.class, () -> service.updateMethod(7L, OWNER, "MOMO"));
    }

    @Test
    void requireSupportedMethod_includesVnpay() {
        assertEquals("VNPAY", PaymentService.requireSupportedMethod(" vnpay "));
        assertThrows(BadRequestException.class, () -> PaymentService.requireSupportedMethod("ZALOPAY"));
    }

    @Test
    void createPendingPayment_keepsTheB04Signature_andTheFullTotal() {
        Order order = new Order();
        order.setTotalPrice(new BigDecimal("255000.00"));
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment created = service.createPendingPayment(order, "momo");

        assertEquals("MOMO", created.getPaymentMethod());
        assertEquals(new BigDecimal("255000.00"), created.getAmount());
        assertEquals(Payment.Status.PENDING, created.getStatus());
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    @Test
    void getPaymentFor_ownerOrStaffWithPermission_only() {
        Payment payment = payment(OrderStatus.PENDING_PAYMENT, Payment.Status.PENDING, "COD");

        assertEquals(payment, service.getPaymentFor(7L, OWNER, false));
        assertEquals(payment, service.getPaymentFor(7L, STRANGER, true));
        assertThrows(ForbiddenException.class, () -> service.getPaymentFor(7L, STRANGER, false));
    }

    @Test
    void listSummaries_joinsPaymentAndRefundStatus_inTwoQueries() {
        Payment payment = payment(OrderStatus.CANCELLED, Payment.Status.SUCCESS, "VNPAY");
        Refund refund = new Refund();
        refund.setOrder(payment.getOrder());
        refund.setStatus(Refund.Status.PENDING);
        when(payments.findByOrderIdIn(List.of(7L))).thenReturn(List.of(payment));
        when(refunds.findByOrderIdIn(List.of(7L))).thenReturn(List.of(refund));

        List<AdminPaymentSummaryResponse> summaries = service.listSummaries(List.of(7L));

        assertEquals(1, summaries.size());
        assertEquals("PENDING", summaries.get(0).refundStatus());
        assertEquals("SUCCESS", summaries.get(0).paymentStatus());
        verify(payments, times(1)).findByOrderIdIn(any());
        verify(refunds, times(1)).findByOrderIdIn(any());
        assertTrue(service.listSummaries(List.of()).isEmpty());
        verify(payments, never()).findByOrderIdIn(eq(List.of()));
    }
}
