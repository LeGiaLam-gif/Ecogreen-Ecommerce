package com.example.backend.service.payment;

import java.math.BigDecimal;

/**
 * What a provider callback says, after its signature was checked.
 *
 * @param signatureValid false when the signature is missing or wrong; every other field is then meaningless
 * @param success        true only when the provider reports a completed payment
 * @param transactionRef the merchant reference of the attempt (null when the callback does not carry one)
 * @param amount         the amount the provider says it collected, in VND (null when missing or unreadable)
 * @param responseCode   the provider's code, for logs
 * @param rawResponse    the callback as received (without the signature); stored write-only, never returned or logged
 */
public record WebhookVerificationResult(boolean signatureValid, boolean success, String transactionRef,
                                        BigDecimal amount, String responseCode, String rawResponse) {

    public static WebhookVerificationResult invalid() {
        return new WebhookVerificationResult(false, false, null, null, null, null);
    }
}
