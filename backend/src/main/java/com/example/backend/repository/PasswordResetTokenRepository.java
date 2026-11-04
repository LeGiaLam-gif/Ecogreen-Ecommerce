package com.example.backend.repository;

import com.example.backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Lookup through the UNIQUE index on token_hash. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Atomic claim: returns 1 only for the single caller that consumed a still-valid (unused, unexpired) token. */
    @Modifying(clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Voids every still-open link of a user (a new request replaces older ones; a finished reset voids the rest). */
    @Modifying(clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidateOpenForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
