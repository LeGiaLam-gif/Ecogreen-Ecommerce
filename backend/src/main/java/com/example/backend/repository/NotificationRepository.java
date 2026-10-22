package com.example.backend.repository;

import com.example.backend.entity.Notification;
import com.example.backend.entity.NotificationStatus;
import com.example.backend.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** The caller's own notifications; backed by {@code idx_notifications_user_created}. The order comes from the Pageable. */
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    /** Admin log filtered by status; backed by {@code idx_notifications_status_created}. */
    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    boolean existsByUserIdAndTypeAndDedupeKey(Long userId, NotificationType type, String dedupeKey);

    /** Moves a row out of PENDING. Must run inside a transaction (the service opens one). */
    @Modifying
    @Query("update Notification n set n.status = :status, n.sentAt = :sentAt, n.failureReason = :reason where n.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") NotificationStatus status,
                     @Param("sentAt") LocalDateTime sentAt, @Param("reason") String reason);
}
