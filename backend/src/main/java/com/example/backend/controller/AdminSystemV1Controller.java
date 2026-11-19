package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.Permissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only system configuration status for authorized administrators (never exposes secrets). */
@RestController
@RequestMapping("/api/v1/admin/system")
public class AdminSystemV1Controller {

    @Autowired private AuthGuard authGuard;

    @Value("${ecogreen.shipping.flat-fee:30000}") private BigDecimal shippingFlatFee;
    @Value("${ecogreen.shipping.free-threshold:500000}") private BigDecimal freeShippingThreshold;
    @Value("${ecogreen.payment.mock-enabled:true}") private boolean paymentMockEnabled;
    @Value("${ecogreen.payment.vnpay.tmn-code:}") private String vnpayTmnCode;
    @Value("${ecogreen.payment.vnpay.secret:}") private String vnpaySecret;
    @Value("${GOOGLE_CLIENT_ID:}") private String googleClientId;
    @Value("${ecogreen.facebook.app-id:}") private String facebookAppId;
    @Value("${ecogreen.facebook.app-secret:}") private String facebookAppSecret;
    @Value("${spring.mail.host:}") private String smtpHost;

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.SYSTEM_CONFIGURE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shippingFlatFee", shippingFlatFee);
        body.put("freeShippingThreshold", freeShippingThreshold);
        body.put("paymentMockEnabled", paymentMockEnabled);
        body.put("vnpayConfigured", isNonBlank(vnpayTmnCode) && isNonBlank(vnpaySecret));
        body.put("googleLoginConfigured", isNonBlank(googleClientId));
        body.put("facebookLoginConfigured", isNonBlank(facebookAppId) && isNonBlank(facebookAppSecret));
        body.put("smtpConfigured", isNonBlank(smtpHost));
        return ApiResponse.of(body);
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
