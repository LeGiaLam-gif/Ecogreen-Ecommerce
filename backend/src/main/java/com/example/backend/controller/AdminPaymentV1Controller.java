package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.AdminPaymentSummaryResponse;
import com.example.backend.dto.CompleteRefundRequest;
import com.example.backend.dto.ConfirmPaymentRequest;
import com.example.backend.dto.PaymentResponse;
import com.example.backend.dto.RefundResponse;
import com.example.backend.exception.BadRequestException;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.security.Permissions;
import com.example.backend.service.PaymentService;
import com.example.backend.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Staff payment actions (B06), each behind its own permission from {@link Permissions}. */
@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentV1Controller {

    static final int MAX_SUMMARY_IDS = 100;

    @Autowired private PaymentService paymentService;
    @Autowired private RefundService refundService;
    @Autowired private AuthGuard authGuard;

    /** Payment and refund status of up to 100 orders (the admin order list asks for its page in one call). */
    @GetMapping
    public ApiResponse<List<AdminPaymentSummaryResponse>> summaries(@RequestParam List<Long> orderIds,
                                                                    HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PAYMENT_VIEW);
        if (orderIds.size() > MAX_SUMMARY_IDS) {
            throw new BadRequestException("Tối đa " + MAX_SUMMARY_IDS + " đơn hàng mỗi lần.");
        }
        return ApiResponse.of(paymentService.listSummaries(orderIds));
    }

    /** Staff confirm that the VietQR / MoMo transfer arrived: payment SUCCESS and order PAID. */
    @PostMapping("/{orderId}/confirm")
    public ApiResponse<PaymentResponse> confirm(@PathVariable Long orderId,
                                                @RequestBody(required = false) @Valid ConfirmPaymentRequest body,
                                                HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PAYMENT_CONFIRM);
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        return ApiResponse.of(PaymentResponse.from(
                paymentService.confirmManually(orderId, current.getUserId(), body == null ? null : body.note()),
                paymentService.isMockEnabled()));
    }

    /** Staff confirm that a pending refund was sent: refund SUCCESS and payment REFUNDED. */
    @PostMapping("/{orderId}/refund/complete")
    public ApiResponse<RefundResponse> completeRefund(@PathVariable Long orderId,
                                                      @RequestBody(required = false) @Valid CompleteRefundRequest body,
                                                      HttpServletRequest request) {
        authGuard.requirePermission(request, Permissions.PAYMENT_REFUND);
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        return ApiResponse.of(RefundResponse.from(
                refundService.completeRefund(orderId, current.getUserId(), body == null ? null : body.providerTransactionId())));
    }
}
