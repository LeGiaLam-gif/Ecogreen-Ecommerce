package com.example.backend.repository;

import com.example.backend.entity.Refund;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** READ-ONLY report queries over {@code refunds} (B09). {@link RefundRepository} belongs to B06 and is not edited here. */
public interface RefundReportRepository extends Repository<Refund, Long> {

    /** One row per status that has at least one refund in the window. */
    interface RefundTotalRow {
        Refund.Status getStatus();

        Long getRefundCount();

        BigDecimal getTotalAmount();
    }

    /**
     * Refund count and amount per status for refunds CREATED in {@code [from, to)}, only for the given statuses (pass the
     * {@link Refund.Status} constants, never a literal). One grouped query.
     */
    @Query("select r.status as status, count(r) as refundCount, sum(r.amount) as totalAmount from Refund r "
            + "where r.status in :statuses and r.createdAt >= :from and r.createdAt < :to group by r.status")
    List<RefundTotalRow> totalsByStatus(@Param("statuses") Collection<Refund.Status> statuses,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
