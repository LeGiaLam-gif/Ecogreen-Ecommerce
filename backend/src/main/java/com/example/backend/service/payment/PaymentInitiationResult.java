package com.example.backend.service.payment;

/**
 * @param redirectUrl    where the browser is sent to pay
 * @param transactionRef the merchant reference of THIS attempt (for VNPay: vnp_TxnRef); unique per attempt
 */
public record PaymentInitiationResult(String redirectUrl, String transactionRef) {
}
