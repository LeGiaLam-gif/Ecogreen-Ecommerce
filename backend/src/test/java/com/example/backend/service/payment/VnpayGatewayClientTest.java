package com.example.backend.service.payment;

import com.example.backend.entity.Order;
import com.example.backend.entity.Payment;
import com.example.backend.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B06: the VNPay 2.1.0 rules - signature, amount x 100, success codes, per-attempt reference, fail-closed configuration. */
class VnpayGatewayClientTest {

    private static final String SECRET = "TEST_ONLY_SECRET_NOT_A_REAL_ONE";
    private static final String URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    // 2026-03-01T17:30:15Z is 2026-03-02 00:30:15 in Asia/Ho_Chi_Minh
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T17:30:15Z"), ZoneId.of("UTC"));

    private final VnpayGatewayClient client = new VnpayGatewayClient("TESTTMN1", SECRET, URL, CLOCK);

    /** An independent HMAC-SHA512 over the sorted, URL-encoded string, written here on purpose (not shared with the client). */
    private static String sign(Map<String, String> params, String secret) throws Exception {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (data.length() > 0) data.append('&');
            data.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII)).append('=')
                .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
        }
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static Map<String, String> callback(String ref, String amountX100, String responseCode, String txnStatus) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TmnCode", "TESTTMN1");
        params.put("vnp_TxnRef", ref);
        params.put("vnp_Amount", amountX100);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", txnStatus);
        params.put("vnp_OrderInfo", "Thanh toan don hang 7");
        params.put("vnp_SecureHash", sign(params, SECRET));
        return params;
    }

    private static Payment payment(String amount) {
        Order order = new Order();
        order.setId(7L);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal(amount));
        return payment;
    }

    private static Map<String, String> query(String url) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void signature_roundTrip_isValid_andTheCallbackIsRead() throws Exception {
        WebhookVerificationResult result = client.verifyAndParse(callback("7-ABC", "25500000", "00", "00"));

        assertTrue(result.signatureValid());
        assertTrue(result.success());
        assertEquals("7-ABC", result.transactionRef());
        assertEquals(0, new BigDecimal("255000").compareTo(result.amount()));
    }

    @Test
    void signature_tamperedAmount_isInvalid() throws Exception {
        Map<String, String> params = callback("7-ABC", "25500000", "00", "00");
        params.put("vnp_Amount", "100"); // changed after signing

        assertFalse(client.verifyAndParse(params).signatureValid());
    }

    @Test
    void signature_missingOrWrongOrSignedWithAnotherSecret_isInvalid() throws Exception {
        Map<String, String> missing = callback("7-ABC", "25500000", "00", "00");
        missing.remove("vnp_SecureHash");
        Map<String, String> wrong = callback("7-ABC", "25500000", "00", "00");
        wrong.put("vnp_SecureHash", "0".repeat(128));
        Map<String, String> otherSecret = callback("7-ABC", "25500000", "00", "00");
        otherSecret.put("vnp_SecureHash", sign(otherSecret, "SOME_OTHER_SECRET"));

        assertFalse(client.verifyAndParse(missing).signatureValid());
        assertFalse(client.verifyAndParse(wrong).signatureValid());
        assertFalse(client.verifyAndParse(otherSecret).signatureValid());
    }

    @Test
    void signature_ignoresSecureHashTypeAndNonVnpParameters() throws Exception {
        Map<String, String> params = callback("7-ABC", "25500000", "00", "00");
        params.put("vnp_SecureHashType", "HmacSHA512");
        params.put("utm_source", "ignored");

        assertTrue(client.verifyAndParse(params).signatureValid());
    }

    @Test
    void successRequiresBothCodesToBe00() throws Exception {
        assertFalse(client.verifyAndParse(callback("7-ABC", "25500000", "24", "02")).success());   // customer cancelled
        assertFalse(client.verifyAndParse(callback("7-ABC", "25500000", "00", "02")).success());   // response ok, transaction not
        assertFalse(client.verifyAndParse(callback("7-ABC", "25500000", "07", "00")).success());   // suspicious
        assertTrue(client.verifyAndParse(callback("7-ABC", "25500000", "00", "00")).success());
        // a failure is still a VALID callback
        assertTrue(client.verifyAndParse(callback("7-ABC", "25500000", "24", "02")).signatureValid());
    }

    @Test
    void amount_isConvertedTimes100_asAnInteger_andBack() {
        assertEquals("25500000", VnpayGatewayClient.toVnpAmount(new BigDecimal("255000.00")));
        assertEquals("100", VnpayGatewayClient.toVnpAmount(new BigDecimal("1.00")));
        assertEquals("0", VnpayGatewayClient.toVnpAmount(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("255000").compareTo(VnpayGatewayClient.fromVnpAmount("25500000")));
        assertEquals(null, VnpayGatewayClient.fromVnpAmount("12.5"));
        assertEquals(null, VnpayGatewayClient.fromVnpAmount(null));
    }

    @Test
    void initiate_buildsASignedRequest_withTheVnpay210Fields() throws Exception {
        PaymentInitiationResult result = client.initiate(payment("255000.00"), "http://localhost:5173/payment/return?orderId=7", "203.0.113.9");

        assertTrue(result.redirectUrl().startsWith(URL + "?"));
        Map<String, String> q = query(result.redirectUrl());
        assertEquals("2.1.0", q.get("vnp_Version"));
        assertEquals("pay", q.get("vnp_Command"));
        assertEquals("TESTTMN1", q.get("vnp_TmnCode"));
        assertEquals("25500000", q.get("vnp_Amount"));
        assertEquals("VND", q.get("vnp_CurrCode"));
        assertEquals(result.transactionRef(), q.get("vnp_TxnRef"));
        assertEquals("http://localhost:5173/payment/return?orderId=7", q.get("vnp_ReturnUrl"));
        assertEquals("203.0.113.9", q.get("vnp_IpAddr"));
        assertEquals("20260302003015", q.get("vnp_CreateDate"));  // Asia/Ho_Chi_Minh, not UTC
        assertEquals("20260302004515", q.get("vnp_ExpireDate"));
        assertTrue(q.get("vnp_OrderInfo").chars().allMatch(c -> c < 128), "OrderInfo must be ASCII");

        String hash = q.remove("vnp_SecureHash");
        assertEquals(sign(q, SECRET), hash);
        assertFalse(result.redirectUrl().contains(SECRET));
    }

    @Test
    void initiate_createsANewReferenceForEveryAttempt() {
        Payment payment = payment("255000.00");

        String first = client.initiate(payment, "http://localhost:5173/payment/return", "127.0.0.1").transactionRef();
        String second = client.initiate(payment, "http://localhost:5173/payment/return", "127.0.0.1").transactionRef();

        assertNotNull(first);
        assertNotEquals(first, second);
        assertTrue(first.length() <= 100);
        assertTrue(first.startsWith("7-"));
    }

    @Test
    void emptySecretOrTmnCode_failsClosed_withoutAStartupCrash() throws Exception {
        VnpayGatewayClient noSecret = new VnpayGatewayClient("TESTTMN1", "", URL, CLOCK);
        VnpayGatewayClient noTmn = new VnpayGatewayClient("", SECRET, URL, CLOCK);
        VnpayGatewayClient blank = new VnpayGatewayClient("  ", "  ", URL, CLOCK);

        for (VnpayGatewayClient c : new VnpayGatewayClient[] {noSecret, noTmn, blank}) {
            assertFalse(c.isAvailable());
            assertThrows(BusinessRuleViolationException.class, () -> c.initiate(payment("1000.00"), "http://x", "127.0.0.1"));
            assertThrows(BusinessRuleViolationException.class, () -> c.verifyAndParse(callback("7-ABC", "100000", "00", "00")));
        }
        assertTrue(client.isAvailable());
    }
}
