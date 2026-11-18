package com.example.backend.service;

import com.example.backend.entity.OrderStatus;
import com.example.backend.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PendingOrderExpiryService {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpiryService.class);
    private static final int EXPIRY_DAYS = 3;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_SCAN = 10;

    private final OrderRepository orderRepository;
    private final PendingOrderExpiryProcessor processor;

    public PendingOrderExpiryService(OrderRepository orderRepository, PendingOrderExpiryProcessor processor) {
        this.orderRepository = orderRepository;
        this.processor = processor;
    }

    /** Processes bounded batches; each order commits or rolls back independently. */
    public void expirePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(EXPIRY_DAYS);
        for (int batch = 0; batch < MAX_BATCHES_PER_SCAN; batch++) {
            List<Long> orderIds = orderRepository.findExpiredOrderIds(
                    OrderStatus.PENDING_PAYMENT, cutoff, PageRequest.of(0, BATCH_SIZE));
            if (orderIds.isEmpty()) {
                return;
            }
            int expired = 0;
            for (Long orderId : orderIds) {
                try {
                    if (processor.expireOne(orderId, cutoff)) {
                        expired++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not expire pending order {}: {}", orderId, e.getClass().getSimpleName());
                }
            }
            if (expired == 0) {
                return;
            }
        }
    }
}
