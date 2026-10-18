package com.example.backend.dto;

import jakarta.validation.constraints.Size;

/** Optional body of POST /api/v1/admin/payments/{orderId}/refund/complete: the bank reference of the transfer staff made. */
public record CompleteRefundRequest(
        @Size(max = 100, message = "Mã giao dịch hoàn tiền tối đa 100 ký tự.") String providerTransactionId) {
}
