package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.InitiatePaymentRequest;
import com.example.backend.dto.InitiatePaymentResponse;
import com.example.backend.dto.MockPayRequest;
import com.example.backend.dto.PaymentResponse;
import com.example.backend.dto.UpdatePaymentMethodRequest;
import com.example.backend.entity.Payment;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.PaymentService;
import com.example.backend.service.payment.PaymentInitiationResult;
import com.example.backend.service.payment.WebhookOutcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payments on the /api/v1 contract (B06). The caller is the user of the verified token; ownership is checked by the service
 * on every {@code /{orderId}}.
 *
 * <p>The webhook is the ONE public endpoint here: no token, protected only by the provider's signature, and the one response
 * that is not the usual envelope (it answers the gateway in the gateway's own JSON {@code {"RspCode","Message"}}).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private static final Logger log = LoggerFactory.getLogger(PaymentV1Controller.class);

    @Autowired private PaymentService paymentService;

    @PostMapping("/{orderId}/initiate")
    public ApiResponse<InitiatePaymentResponse> initiate(@PathVariable Long orderId,
                                                         @RequestBody(required = false) @Valid InitiatePaymentRequest body,
                                                         HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        PaymentInitiationResult result = paymentService.initiatePayment(orderId, current.getUserId(),
                body == null ? null : body.returnUrl(), clientIp(request));
        Payment payment = paymentService.getByOrderId(orderId);
        return ApiResponse.of(new InitiatePaymentResponse(result.redirectUrl(), payment.getProvider()));
    }

    /** The payment of an order: its owner, or staff with {@code payment:view}. */
    @GetMapping("/{orderId}")
    public ApiResponse<PaymentResponse> get(@PathVariable Long orderId, HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        Payment payment = paymentService.getPaymentFor(orderId, current.getUserId(),
                current.hasPermission(Permissions.PAYMENT_VIEW));
        return ApiResponse.of(PaymentResponse.from(payment, paymentService.isMockEnabled()));
    }

    @PutMapping("/{orderId}/method")
    public ApiResponse<PaymentResponse> updateMethod(@PathVariable Long orderId,
                                                     @Valid @RequestBody UpdatePaymentMethodRequest body,
                                                     HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        Payment payment = paymentService.updateMethod(orderId, current.getUserId(), body.method());
        return ApiResponse.of(PaymentResponse.from(payment, paymentService.isMockEnabled()));
    }

    /** TEMPORARY-COMPAT(B06): development only (answers 400 unless ecogreen.payment.mock-enabled=true). */
    @PostMapping("/{orderId}/mock-pay")
    public ApiResponse<PaymentResponse> mockPay(@PathVariable Long orderId, @Valid @RequestBody MockPayRequest body,
                                                HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        paymentService.mockPay(orderId, current.getUserId(), "SUCCESS".equals(body.outcome()));
        return ApiResponse.of(PaymentResponse.from(paymentService.getByOrderId(orderId), paymentService.isMockEnabled()));
    }

    /**
     * Public provider callback (VNPay IPN is a GET with query parameters). NO AuthGuard: the signature is the only protection.
     * Answers HTTP 200 with the gateway's JSON; code 99 means "could not record, deliver again".
     */
    @RequestMapping(value = "/webhook/{provider}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> webhook(@PathVariable String provider, HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        try {
            return ResponseEntity.ok(gatewayAnswer(paymentService.handleWebhook(provider, params)));
        } catch (com.example.backend.exception.ResourceNotFoundException e) {
            throw e; // unknown provider: the standard 404
        } catch (RuntimeException e) {
            // The payment and order changes were rolled back; the provider will deliver again. Never log the parameters.
            log.error("Payment webhook could not be recorded (provider {}): {}", provider, e.getClass().getSimpleName());
            return ResponseEntity.ok(answer("99", "Unknown error"));
        }
    }

    static Map<String, String> gatewayAnswer(WebhookOutcome outcome) {
        return switch (outcome) {
            case PROCESSED -> answer("00", "Confirm Success");
            case ALREADY_PROCESSED -> answer("02", "Order already confirmed");
            case INVALID_SIGNATURE -> answer("97", "Invalid signature");
            case PAYMENT_NOT_FOUND -> answer("01", "Order not found");
            case AMOUNT_MISMATCH -> answer("04", "Invalid amount");
        };
    }

    private static Map<String, String> answer(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("RspCode", code);
        body.put("Message", message);
        return body;
    }

    private static String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null || ip.contains(":") ? "127.0.0.1" : ip; // VNPay expects IPv4; X-Forwarded-For is not trusted here
    }

    private static CurrentUser requireCurrent(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        }
        return current;
    }
}
