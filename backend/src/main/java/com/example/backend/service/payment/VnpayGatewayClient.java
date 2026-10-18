package com.example.backend.service.payment;

import com.example.backend.entity.Payment;
import com.example.backend.exception.BusinessRuleViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * VNPay sandbox / production, API version 2.1.0. Signature: HMAC-SHA512 over the key-sorted, URL-encoded {@code k=v&k=v}
 * string of all {@code vnp_*} parameters except {@code vnp_SecureHash} and {@code vnp_SecureHashType}.
 *
 * <p>Fails closed: when the terminal code or the secret is empty, {@link #initiate} and {@link #verifyAndParse} throw a
 * {@link BusinessRuleViolationException}; the application still starts. The secret is never logged or returned.
 */
@Component
public class VnpayGatewayClient implements PaymentGatewayClient {

    public static final String PROVIDER = "VNPAY";

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String HMAC = "HmacSHA512";

    private final String tmnCode;
    private final String secret;
    private final String payUrl;
    private final Clock clock;

    @Autowired
    public VnpayGatewayClient(@Value("${ecogreen.payment.vnpay.tmn-code:}") String tmnCode,
                              @Value("${ecogreen.payment.vnpay.secret:}") String secret,
                              @Value("${ecogreen.payment.vnpay.pay-url:}") String payUrl) {
        this(tmnCode, secret, payUrl, Clock.system(VIETNAM));
    }

    VnpayGatewayClient(String tmnCode, String secret, String payUrl, Clock clock) {
        this.tmnCode = tmnCode == null ? "" : tmnCode.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.payUrl = payUrl == null ? "" : payUrl.trim();
        this.clock = clock;
    }

    @Override
    public String providerCode() {
        return PROVIDER;
    }

    @Override
    public boolean isAvailable() {
        return !tmnCode.isEmpty() && !secret.isEmpty() && !payUrl.isEmpty();
    }

    @Override
    public PaymentInitiationResult initiate(Payment payment, String returnUrl, String clientIp) {
        requireConfigured();
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new BusinessRuleViolationException("Số tiền thanh toán không hợp lệ.");
        }
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(VIETNAM));
        Long orderId = payment.getOrder().getId();
        // Unique PER ATTEMPT: VNPay refuses to reuse a vnp_TxnRef, so every initiate gets a new one (<= 100 chars).
        String txnRef = orderId + "-" + STAMP.format(now) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", toVnpAmount(payment.getAmount()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + orderId); // ASCII only
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", STAMP.format(now));
        params.put("vnp_ExpireDate", STAMP.format(now.plusMinutes(15)));

        String query = canonical(params);
        String url = payUrl + (payUrl.contains("?") ? "&" : "?") + query + "&vnp_SecureHash=" + sign(query);
        return new PaymentInitiationResult(url, txnRef);
    }

    @Override
    public WebhookVerificationResult verifyAndParse(Map<String, String> rawParams) {
        requireConfigured();
        if (rawParams == null) {
            return WebhookVerificationResult.invalid();
        }
        String received = rawParams.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return WebhookVerificationResult.invalid();
        }
        Map<String, String> signed = new TreeMap<>();
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")) {
                signed.put(key, entry.getValue());
            }
        }
        String canonical = canonical(signed);
        byte[] expected = sign(canonical).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = received.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) { // constant time
            return WebhookVerificationResult.invalid();
        }

        boolean success = "00".equals(signed.get("vnp_ResponseCode")) && "00".equals(signed.get("vnp_TransactionStatus"));
        return new WebhookVerificationResult(true, success, signed.get("vnp_TxnRef"), fromVnpAmount(signed.get("vnp_Amount")),
                signed.get("vnp_ResponseCode"), canonical);
    }

    /** VNPay wants the amount times 100 as an integer string: 255000.00 VND becomes "25500000". */
    static String toVnpAmount(BigDecimal amount) {
        return amount.movePointRight(2).toBigIntegerExact().toString();
    }

    /** Inverse of {@link #toVnpAmount}; null when the value is missing or not an integer. */
    static BigDecimal fromVnpAmount(String vnpAmount) {
        if (vnpAmount == null || !vnpAmount.matches("\\d{1,18}")) {
            return null;
        }
        return new BigDecimal(vnpAmount).movePointLeft(2);
    }

    private void requireConfigured() {
        if (!isAvailable()) {
            throw new BusinessRuleViolationException("Cổng thanh toán VNPAY chưa được cấu hình.");
        }
    }

    /** Key-sorted {@code k=v&k=v} with US-ASCII URL encoding; empty values are skipped (VNPay does the same). */
    private static String canonical(Map<String, String> params) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('&');
            }
            out.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII))
               .append('=')
               .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return out.toString();
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA512 is not available", e);
        }
    }
}
