package com.example.backend.repository;

import com.example.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * TEMPORARY-COMPAT(B08): revokes / removes every refresh token of one user. B08's spec calls
 * {@code RefreshTokenService.revokeAllForUser}, but that method does not exist in B01 and B01 internals are out of B08's
 * scope, so B08 owns this single-purpose repository over the existing {@link RefreshToken} entity. Remove it (and switch
 * {@code UserService} to the B01 service) when B01 publishes {@code revokeAllForUser}
 * (see docs/ai/contracts/B08-customer.md, "Requests for contract changes").
 */
public interface UserSessionRepository extends Repository<RefreshToken, Long> {

    /** Revokes every still-active refresh token of the user (all families). Returns the number revoked. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);

    /** Hard delete of the user's token rows (when the account itself is deleted). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
