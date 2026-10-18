package com.example.backend.dto;

import com.example.backend.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment as the API shows it. The gateway's raw answer ({@code raw_response}) is stored write-only and never appears here.
 * {@code mockEnabled} tells the UI whether the development "mock pay" buttons may be offered.
 */
public class PaymentResponse {
    public Long id;
    public Long orderId;
    public String paymentMethod;
    public BigDecimal amount;
    public String status;
    public String transactionId;
    public String provider;
    public LocalDateTime paidAt;
    public boolean mockEnabled;

    public static PaymentResponse from(Payment p) {
        return from(p, false);
    }

    public static PaymentResponse from(Payment p, boolean mockEnabled) {
        PaymentResponse r = new PaymentResponse();
        r.id = p.getId();
        r.orderId = p.getOrder().getId();
        r.paymentMethod = p.getPaymentMethod();
        r.amount = p.getAmount();
        r.status = p.getStatus().name();
        r.transactionId = p.getTransactionId();
        r.provider = p.getProvider();
        r.paidAt = p.getPaidAt();
        r.mockEnabled = mockEnabled;
        return r;
    }
}
