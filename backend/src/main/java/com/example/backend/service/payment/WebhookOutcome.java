package com.example.backend.service.payment;

/** How {@code PaymentService.handleWebhook} treated a callback. A failure to record it is an exception, never an outcome. */
public enum WebhookOutcome {
    /** Recorded: the payment is SUCCESS or FAILED and the order was moved through the state machine. */
    PROCESSED,
    /** The payment is no longer PENDING: a repeated delivery, nothing was changed. */
    ALREADY_PROCESSED,
    /** The signature is missing or wrong: nothing was changed. */
    INVALID_SIGNATURE,
    /** No payment has this provider and reference: nothing was changed. */
    PAYMENT_NOT_FOUND,
    /** The amount differs from the payment's amount: nothing was changed. */
    AMOUNT_MISMATCH
}
