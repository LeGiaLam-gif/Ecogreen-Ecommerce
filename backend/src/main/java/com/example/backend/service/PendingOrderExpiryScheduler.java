package com.example.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingOrderExpiryScheduler {

    private final PendingOrderExpiryService expiryService;

    public PendingOrderExpiryScheduler(PendingOrderExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expirePendingOrders() {
        expiryService.expirePendingOrders();
    }
}
