package com.example.backend.service;

import com.example.backend.dto.AdminPaymentSummaryResponse;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Refund;
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
import com.example.backend.service.payment.VnpayGatewayClient;
import com.example.backend.service.payment.WebhookOutcome;
import com.example.backend.service.payment.WebhookVerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Payments (B06). One payment row per order. Online payment (VNPAY) goes through a {@link PaymentGatewayClient} and is
 * confirmed ONLY by the signed webhook ({@link #handleWebhook}); the browser returning from the gateway proves nothing.
 * COD needs no payment step; VIETQR and MOMO are static QR codes with no confirmation signal, so staff confirm them
 * ({@link #confirmManually}). The order status is changed only through {@code OrderService.transitionStatus}.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Every method an order may be created with or switched to. B06 adds VNPAY. */
    static final Set<String> SUPPORTED_METHODS = Set.of("COD", "VIETQR", "MOMO", "VNPAY");
    /** Methods paid through a {@link PaymentGatewayClient}. */
    static final Set<String> GATEWAY_METHODS = Set.of(VnpayGatewayClient.PROVIDER);
    /** Methods staff confirm by hand (payment:confirm). */
    static final Set<String> MANUAL_CONFIRM_METHODS = Set.of("VIETQR", "MOMO");

    static final String WEBHOOK_PAID_NOTE = "Auto by payment webhook";
    static final String WEBHOOK_FAILED_NOTE = "Payment failed";
    static final String MANUAL_CONFIRM_NOTE = "Xác nhận đã nhận tiền";

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private OrderService orderService;
    @Autowired private PaymentGatewayRegistry gateways;

    /** Browser return page; a client-supplied returnUrl is honoured only when it starts with this (open-redirect guard). */
    @Value("${ecogreen.payment.return-url:http://localhost:5173/payment/return}")
    private String returnUrlBase;

    /** TEMPORARY-COMPAT(B06): enables the mock provider and the dev "mock pay" action. */
    @Value("${ecogreen.payment.mock-enabled:false}")
    private boolean mockEnabled;

    /**
     * The canonical (trimmed, upper-case) form of {@code method}.
     *
     * @throws BadRequestException when it is missing or not one of {@link #SUPPORTED_METHODS}
     */
    public static String requireSupportedMethod(String method) {
        String normalised = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_METHODS.contains(normalised)) {
            throw new BadRequestException("Phương thức thanh toán không hợp lệ.");
        }
        return normalised;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    /**
     * Creates the PENDING payment of a new order for its full total. Called by CheckoutService inside the checkout
     * transaction (it joins it, so the payment rolls back with the order). B04 contract: the signature does not change.
     */
    @Transactional
    public Payment createPendingPayment(Order order, String method) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(requireSupportedMethod(method));
        payment.setAmount(order.getTotalPrice());
        payment.setStatus(Payment.Status.PENDING);
        return paymentRepository.save(payment);
    }

    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán cho đơn hàng #" + orderId));
    }

    /** The payment of an order for its owner or for staff with {@code payment:view}; anyone else gets 403. */
    @Transactional(readOnly = true)
    public Payment getPaymentFor(Long orderId, Long userId, boolean canViewAny) {
        Payment payment = getByOrderId(orderId);
        if (!canViewAny) {
            requireOwner(payment, userId);
        }
        return payment;
    }

    /**
     * Starts (or restarts) an online payment for the order's owner. Each call creates a NEW transaction reference, so an
     * attempt the customer abandoned never collides with the next one.
     *
     * @param returnUrl the browser return page requested by the client; used only when it is under the configured base
     * @throws ForbiddenException             not the owner (403)
     * @throws BusinessRuleViolationException order not PENDING_PAYMENT, payment already settled, or the method has no gateway
     */
    @Transactional
    public PaymentInitiationResult initiatePayment(Long orderId, Long userId, String returnUrl, String clientIp) {
        Payment payment = lockedPayment(orderId);
        requireOwner(payment, userId);
        if (payment.getOrder().getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleViolationException("Đơn hàng không ở trạng thái chờ thanh toán.");
        }
        if (payment.getStatus() != Payment.Status.PENDING && payment.getStatus() != Payment.Status.FAILED) {
            throw new BusinessRuleViolationException("Đơn hàng này đã được thanh toán.");
        }
        if (!GATEWAY_METHODS.contains(payment.getPaymentMethod())) {
            throw new BusinessRuleViolationException("Phương thức thanh toán này không dùng cổng thanh toán trực tuyến.");
        }

        PaymentGatewayClient client = gatewayFor(payment.getPaymentMethod());
        PaymentInitiationResult result = client.initiate(payment, resolveReturnUrl(returnUrl, orderId), clientIp);

        payment.setProvider(client.providerCode());
        payment.setTransactionId(result.transactionRef());
        payment.setStatus(Payment.Status.PENDING);
        paymentRepository.save(payment);
        return result;
    }

    /**
     * Processes a provider callback. The order of the steps is part of the contract:
     * <ol>
     *   <li>verify the signature - invalid: reject, nothing is read or written;</li>
     *   <li>load the payment by (provider, reference) WITH a pessimistic row lock;</li>
     *   <li>status other than PENDING: already processed - never touch the order a second time;</li>
     *   <li>the amount must equal the payment's amount, otherwise reject;</li>
     *   <li>success: SUCCESS, paid_at, raw response, then the order goes to PAID;</li>
     *   <li>failure: FAILED, then the order goes to CANCELLED (open decision: the alternative is to keep it payable).</li>
     * </ol>
     * If the order transition throws, the exception propagates and this whole transaction rolls back, so the caller answers
     * a retryable code and the provider delivers again.
     *
     * @throws com.example.backend.exception.ResourceNotFoundException unknown provider
     */
    @Transactional
    public WebhookOutcome handleWebhook(String providerCode, Map<String, String> params) {
        PaymentGatewayClient client = gateways.get(providerCode);

        WebhookVerificationResult result = client.verifyAndParse(params); // (1)
        if (!result.signatureValid()) {
            log.warn("Payment webhook rejected: invalid signature (provider {})", client.providerCode());
            return WebhookOutcome.INVALID_SIGNATURE;
        }
        if (result.transactionRef() == null) {
            return WebhookOutcome.PAYMENT_NOT_FOUND;
        }

        Payment payment = paymentRepository // (2)
                .findByProviderAndTransactionIdForUpdate(client.providerCode(), result.transactionRef()).orElse(null);
        if (payment == null) {
            log.warn("Payment webhook for an unknown reference {} (provider {})", result.transactionRef(), client.providerCode());
            return WebhookOutcome.PAYMENT_NOT_FOUND;
        }
        if (payment.getStatus() != Payment.Status.PENDING) { // (3)
            return WebhookOutcome.ALREADY_PROCESSED;
        }
        if (result.amount() == null || payment.getAmount().compareTo(result.amount()) != 0) { // (4)
            log.warn("Payment webhook amount mismatch for payment {} (provider {})", payment.getId(), client.providerCode());
            return WebhookOutcome.AMOUNT_MISMATCH;
        }

        Long orderId = payment.getOrder().getId();
        payment.setRawResponse(result.rawResponse());
        if (result.success()) { // (5)
            payment.setStatus(Payment.Status.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            payment.setPaymentMethod(VnpayGatewayClient.PROVIDER); // the money arrived through the gateway, whatever was selected since
            paymentRepository.save(payment);
            orderService.transitionStatus(orderId, OrderStatus.PAID, null, WEBHOOK_PAID_NOTE);
        } else { // (6)
            payment.setStatus(Payment.Status.FAILED);
            paymentRepository.save(payment);
            orderService.transitionStatus(orderId, OrderStatus.CANCELLED, null, WEBHOOK_FAILED_NOTE);
        }
        return WebhookOutcome.PROCESSED;
    }

    /**
     * Staff confirm that the money of a VIETQR / MOMO order arrived (permission {@code payment:confirm} is the controller's job).
     *
     * @throws BusinessRuleViolationException the method is COD or a gateway method, or the payment is not PENDING
     */
    @Transactional
    public Payment confirmManually(Long orderId, Long actorUserId, String note) {
        Payment payment = lockedPayment(orderId);
        if (!MANUAL_CONFIRM_METHODS.contains(payment.getPaymentMethod())) {
            throw new BusinessRuleViolationException("Chỉ xác nhận thủ công cho thanh toán chuyển khoản VietQR hoặc MoMo.");
        }
        if (payment.getStatus() != Payment.Status.PENDING) {
            throw new BusinessRuleViolationException("Khoản thanh toán này không ở trạng thái chờ xác nhận.");
        }
        payment.setStatus(Payment.Status.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);
        orderService.transitionStatus(orderId, OrderStatus.PAID, actorUserId,
                note == null || note.isBlank() ? MANUAL_CONFIRM_NOTE : note.trim());
        return payment;
    }

    /**
     * TEMPORARY-COMPAT(B06): the development "mock pay" action; replaces the legacy {@code payNow} mock. It only works when
     * {@code ecogreen.payment.mock-enabled=true} and the attempt was started with the MOCK provider, and it goes through the
     * SAME {@link #handleWebhook} path as a real gateway callback (signed with the in-process key).
     * Removal condition: the project no longer needs to run the payment flow without a VNPay sandbox account.
     */
    @Transactional
    public WebhookOutcome mockPay(Long orderId, Long userId, boolean success) {
        if (!mockEnabled) {
            throw new BusinessRuleViolationException("Thanh toán giả lập đang tắt.");
        }
        Payment payment = getByOrderId(orderId);
        requireOwner(payment, userId);
        if (!MockGatewayClient.PROVIDER.equals(payment.getProvider()) || payment.getTransactionId() == null
                || payment.getStatus() != Payment.Status.PENDING) {
            throw new BusinessRuleViolationException("Hãy bắt đầu thanh toán trực tuyến trước khi giả lập kết quả.");
        }
        MockGatewayClient mock = (MockGatewayClient) gateways.get(MockGatewayClient.PROVIDER);
        Map<String, String> callback = mock.signedCallback(payment.getTransactionId(), payment.getAmount(), success);
        return handleWebhook(MockGatewayClient.PROVIDER, callback);
    }

    /**
     * Switches the payment method of an order that is still waiting for payment (owner only). An online attempt that was
     * already started keeps its reference: if its callback still arrives and the money was collected, it is recorded.
     */
    @Transactional
    public Payment updateMethod(Long orderId, Long userId, String method) {
        String normalised = requireSupportedMethod(method);
        Payment payment = lockedPayment(orderId);
        requireOwner(payment, userId);
        if (payment.getOrder().getStatus() != OrderStatus.PENDING_PAYMENT || payment.getStatus() == Payment.Status.SUCCESS
                || payment.getStatus() == Payment.Status.REFUNDED) {
            throw new BusinessRuleViolationException("Đơn hàng không còn chờ thanh toán, không thể đổi phương thức.");
        }
        payment.setPaymentMethod(normalised);
        return paymentRepository.save(payment);
    }

    /** Payment and refund status of many orders in two queries (admin order list). Orders without a payment are omitted. */
    @Transactional(readOnly = true)
    public List<AdminPaymentSummaryResponse> listSummaries(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> refundStatus = new HashMap<>();
        for (Refund refund : refundRepository.findByOrderIdIn(orderIds)) {
            refundStatus.merge(refund.getOrder().getId(), refund.getStatus().name(),
                    (old, latest) -> "FAILED".equals(latest) ? old : latest); // a later non-FAILED refund wins
        }
        return paymentRepository.findByOrderIdIn(orderIds).stream()
                .map(p -> new AdminPaymentSummaryResponse(p.getOrder().getId(), p.getPaymentMethod(), p.getStatus().name(),
                        p.getAmount(), refundStatus.get(p.getOrder().getId())))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------------------

    private Payment lockedPayment(Long orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán cho đơn hàng #" + orderId));
    }

    private static void requireOwner(Payment payment, Long userId) {
        if (!Objects.equals(payment.getOrder().getUserId(), userId)) {
            throw new ForbiddenException("Bạn không có quyền truy cập đơn hàng này.");
        }
    }

    /** VNPAY when it is configured, else the mock provider when enabled, else VNPAY (which then fails closed). */
    private PaymentGatewayClient gatewayFor(String method) {
        PaymentGatewayClient vnpay = gateways.get(VnpayGatewayClient.PROVIDER);
        if (vnpay.isAvailable() || !mockEnabled) {
            return vnpay;
        }
        return gateways.find(MockGatewayClient.PROVIDER).orElse(vnpay);
    }

    /** The configured return page, or {@code requested} when it lies under it; {@code orderId} is appended for the page. */
    String resolveReturnUrl(String requested, Long orderId) {
        String base = returnUrlBase == null ? "" : returnUrlBase.trim();
        String url = base;
        if (requested != null && !base.isEmpty() && requested.startsWith(base)) {
            String rest = requested.substring(base.length());
            if (rest.isEmpty() || rest.startsWith("?") || rest.startsWith("#") || rest.startsWith("/")) {
                url = requested;
            }
        }
        return url + (url.contains("?") ? "&" : "?") + "orderId=" + orderId;
    }
}
