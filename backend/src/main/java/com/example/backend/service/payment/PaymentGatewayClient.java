package com.example.backend.service.payment;

import com.example.backend.entity.Payment;

import java.util.Map;

/**
 * One online payment provider (B06). {@link com.example.backend.service.PaymentService} owns the business rules (ownership,
 * order status, idempotency, amounts); an implementation only knows how to build the provider's request and how to verify
 * and read the provider's answer. Implementations use the JDK only (HMAC), no extra library.
 */
public interface PaymentGatewayClient {

    /** The upper-case provider code stored in {@code payments.provider} and used in {@code /payments/webhook/{provider}}. */
    String providerCode();

    /**
     * False when the provider cannot be used because it is not configured. Callers fall back (mock) or let
     * {@link #initiate} fail closed.
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Builds the provider's payment request for one attempt. The result carries a NEW merchant reference for this attempt.
     * Does not change {@code payment}; the service stores the reference.
     *
     * @throws com.example.backend.exception.BusinessRuleViolationException when the provider is not configured
     */
    PaymentInitiationResult initiate(Payment payment, String returnUrl, String clientIp);

    /**
     * Verifies the signature of a provider callback and reads it. Must not touch the database. An invalid signature is a
     * normal result ({@link WebhookVerificationResult#signatureValid()} false), not an exception.
     *
     * @throws com.example.backend.exception.BusinessRuleViolationException when the provider is not configured (fails closed)
     */
    WebhookVerificationResult verifyAndParse(Map<String, String> params);
}
