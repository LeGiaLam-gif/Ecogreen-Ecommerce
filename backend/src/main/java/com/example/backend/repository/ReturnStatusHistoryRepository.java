package com.example.backend.repository;

import com.example.backend.entity.ReturnStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReturnStatusHistoryRepository extends JpaRepository<ReturnStatusHistory, Long> {

    /** The timeline of one return, oldest first; served by idx_return_status_history_return_changed (return_id, changed_at). */
    @Query("SELECT h FROM ReturnStatusHistory h WHERE h.returnRequest.id = :returnId ORDER BY h.changedAt ASC, h.id ASC")
    List<ReturnStatusHistory> findTimeline(@Param("returnId") Long returnId);
}
