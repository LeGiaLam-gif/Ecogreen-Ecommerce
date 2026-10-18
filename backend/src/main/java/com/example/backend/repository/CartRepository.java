package com.example.backend.repository;

import com.example.backend.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    /**
     * The user's cart row locked until the end of the transaction ({@code SELECT ... FOR UPDATE}). Checkout and every cart
     * change take it, so a second checkout of the same cart waits, then finds the cart empty, and a cart change can never
     * slip in between the moment checkout reads the lines and the moment it clears them.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);
}
