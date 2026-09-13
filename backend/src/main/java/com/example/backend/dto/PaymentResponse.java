package com.example.backend.dto;

import com.example.backend.entity.Payment;
import java.math.BigDecimal;

public class PaymentResponse {
    public Long id;
    public Long orderId;
    public String paymentMethod;
    public BigDecimal amount;
    public String status;
    public String transactionId;

    public static PaymentResponse from(Payment p) {
        PaymentResponse r = new PaymentResponse();
        r.id = p.getId();
        r.orderId = p.getOrder().getId();
        r.paymentMethod = p.getPaymentMethod();
        r.amount = p.getAmount();
        r.status = p.getStatus().name();
        r.transactionId = p.getTransactionId();
        return r;
    }
}
