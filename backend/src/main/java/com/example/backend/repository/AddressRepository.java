package com.example.backend.repository;

import com.example.backend.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** The caller's whole address book (at most 10 rows): the default first, then the newest. Uses idx_addresses_user_id. */
    @Query("SELECT a FROM Address a WHERE a.userId = :userId ORDER BY a.isDefault DESC, a.id DESC")
    List<Address> findAllByUser(@Param("userId") Long userId);

    long countByUserId(Long userId);

    /** The most recently created remaining address of the user (promoted when the default is deleted). */
    Optional<Address> findFirstByUserIdOrderByIdDesc(Long userId);

    /**
     * Clears the default flag of every OTHER address of the user. Runs (and flushes pending changes first) before the new
     * default is saved, so the partial unique index never sees two defaults. The persistence context is NOT cleared: the
     * address that becomes the default stays managed.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Address a SET a.isDefault = false, a.updatedAt = :now "
            + "WHERE a.userId = :userId AND a.isDefault = true AND a.id <> :keepId")
    int clearDefaultsExcept(@Param("userId") Long userId, @Param("keepId") Long keepId, @Param("now") LocalDateTime now);

    /** Used when a user is hard-deleted, so the result does not depend on the V70 foreign key being present. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Address a WHERE a.userId = :userId")
    int deleteAllByUser(@Param("userId") Long userId);
}
