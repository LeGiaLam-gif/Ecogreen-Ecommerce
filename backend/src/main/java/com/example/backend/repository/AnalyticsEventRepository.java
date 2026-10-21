package com.example.backend.repository;

import com.example.backend.entity.AnalyticsEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    /**
     * Events of one type in {@code [from, to)}; backed by {@code idx_analytics_events_type_created (event_type, created_at)}.
     * The order comes from the {@link Pageable} (whitelisted by the service), never from client text.
     */
    @Query("select e from AnalyticsEvent e where e.eventType = :type and e.createdAt >= :from and e.createdAt < :to")
    Page<AnalyticsEvent> findByTypeInRange(@Param("type") String type, @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to, Pageable pageable);

    /** Events of every type in {@code [from, to)}. The service bounds the window (at most 366 days). */
    @Query("select e from AnalyticsEvent e where e.createdAt >= :from and e.createdAt < :to")
    Page<AnalyticsEvent> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
