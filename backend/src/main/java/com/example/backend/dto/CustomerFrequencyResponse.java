package com.example.backend.dto;

import com.example.backend.repository.OrderReportRepository.CustomerFrequencyRow;
import com.example.backend.service.ReportTime;

import java.math.BigDecimal;
import java.time.Instant;

/** Purchase frequency of one customer. No name or email on purpose: analytics:view must not expose personal data. */
public record CustomerFrequencyResponse(Long userId, long orderCount, BigDecimal totalSpent,
                                        Instant firstOrderAt, Instant lastOrderAt) {

    public static CustomerFrequencyResponse from(CustomerFrequencyRow row) {
        BigDecimal spent = row.getTotalSpent() == null ? BigDecimal.ZERO : row.getTotalSpent();
        return new CustomerFrequencyResponse(row.getUserId(), row.getOrderCount() == null ? 0L : row.getOrderCount(),
                spent, ReportTime.toUtc(row.getFirstOrderAt()), ReportTime.toUtc(row.getLastOrderAt()));
    }
}
