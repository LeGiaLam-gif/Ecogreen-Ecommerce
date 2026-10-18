package com.example.backend.service.payment;

import com.example.backend.entity.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TEMPORARY-COMPAT(B06): a local stand-in for a real provider, a bean only when {@code ecogreen.payment.mock-enabled=true}
 * (the development default). It is NOT a shortcut: its callbacks are signed with a key generated at start-up and known only
 * to this process, and they are processed by the same {@code PaymentService.handleWebhook} as VNPay. Nobody outside the
 * process can forge one through the public webhook URL.
 * Removal condition: the project no longer needs to run the payment flow without a VNPay sandbox account.
 */
@Component
@ConditionalOnProperty(name = "ecogreen.payment.mock-enabled", havingValue = "true")
public class MockGatewayClient implements PaymentGatewayClient {

    public static final String PROVIDER = "MOCK";
    private static final String HMAC = "HmacSHA256";

    private final byte[] key = new byte[32];

    public MockGatewayClient() {
        new SecureRandom().nextBytes(key);
    }

    @Override
    public String providerCode() {
        return PROVIDER;
    }

    /** No external page: the browser goes straight back to the return page, where the dev "mock pay" buttons are offered. */
    @Override
    public PaymentInitiationResult initiate(Payment payment, String returnUrl, String clientIp) {
        String ref = "MOCK-" + payment.getOrder().getId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new PaymentInitiationResult(returnUrl, ref);
    }

    /** The callback the mock "gateway" would send for {@code ref}, signed with this process's key. */
    public Map<String, String> signedCallback(String ref, BigDecimal amount, boolean success) {
        Map<String, String> params = new HashMap<>();
        params.put("mock_ref", ref);
        params.put("mock_amount", amount.toPlainString());
        params.put("mock_status", success ? "00" : "01");
        params.put("mock_sig", sign(ref, amount.toPlainString(), params.get("mock_status")));
        return params;
    }

    @Override
    public WebhookVerificationResult verifyAndParse(Map<String, String> params) {
        if (params == null) {
            return WebhookVerificationResult.invalid();
        }
        String ref = params.get("mock_ref");
        String amount = params.get("mock_amount");
        String status = params.get("mock_status");
        String sig = params.get("mock_sig");
        if (ref == null || amount == null || status == null || sig == null) {
            return WebhookVerificationResult.invalid();
        }
        byte[] expected = sign(ref, amount, status).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII))) {
            return WebhookVerificationResult.invalid();
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(amount);
        } catch (NumberFormatException e) {
            parsed = null;
        }
        return new WebhookVerificationResult(true, "00".equals(status), ref, parsed, status,
                "mock_ref=" + ref + "&mock_amount=" + amount + "&mock_status=" + status);
    }

    private String sign(String ref, String amount, String status) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            byte[] digest = mac.doFinal((ref + "|" + amount + "|" + status).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
