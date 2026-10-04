package com.example.backend.repository;

import com.example.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Lookup through the UNIQUE index on token_hash. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Atomic "claim": returns 1 only for the single caller that revoked a still-active token. */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.id = :id and t.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.replacedById = :newId where t.id = :id")
    int markReplaced(@Param("id") Long id, @Param("newId") Long newId);

    /** Reuse detection: revoke every token of the family. */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.familyId = :familyId and t.revoked = false")
    int revokeFamily(@Param("familyId") String familyId);
}
