package com.example.backend.service;

import com.example.backend.entity.Payment;
import com.example.backend.entity.Refund;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Refund records (B06). A refund is created PENDING for the full payment when a PAID order is cancelled; staff send the money
 * (outside the system: no automatic gateway refund) and then complete it here.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    static final String CANCELLED_REASON = "Đơn hàng đã thanh toán bị hủy";

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;

    /**
     * Creates ONE PENDING refund for the full payment amount of a cancelled, paid order, in its own transaction (the cancel is
     * already committed). Does nothing when the order already has a non-FAILED refund; the table's partial unique index backs this
     * up against two concurrent callers.
     *
     * @return true when a row was created
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createPendingRefundIfAbsent(Long orderId) {
        if (refundRepository.existsByOrderIdAndStatusNot(orderId, Refund.Status.FAILED)) {
            return false;
        }
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || payment.getStatus() != Payment.Status.SUCCESS) {
            log.warn("No refund created for order {}: no SUCCESS payment", orderId);
            return false;
        }
        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setOrder(payment.getOrder());
        refund.setAmount(payment.getAmount());
        refund.setStatus(Refund.Status.PENDING);
        refund.setReason(CANCELLED_REASON);
        refundRepository.save(refund);
        return true;
    }

    /**
     * Staff confirm the money was sent back: the refund becomes SUCCESS and the payment REFUNDED.
     *
     * @param providerTransactionId optional bank reference of the transfer
     * @throws ResourceNotFoundException the order has no PENDING refund
     */
    @Transactional
    public Refund completeRefund(Long orderId, Long actorUserId, String providerTransactionId) {
        Refund refund = refundRepository.findByOrderIdAndStatusForUpdate(orderId, Refund.Status.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Không có yêu cầu hoàn tiền đang chờ cho đơn hàng #" + orderId));
        refund.setStatus(Refund.Status.SUCCESS);
        refund.setRefundedAt(LocalDateTime.now());
        if (providerTransactionId != null && !providerTransactionId.isBlank()) {
            refund.setProviderTransactionId(providerTransactionId.trim());
        }
        refundRepository.save(refund);

        Payment payment = refund.getPayment();
        payment.setStatus(Payment.Status.REFUNDED);
        paymentRepository.save(payment);
        log.info("Refund {} of order {} completed by user {}", refund.getId(), orderId, actorUserId);
        return refund;
    }
}
