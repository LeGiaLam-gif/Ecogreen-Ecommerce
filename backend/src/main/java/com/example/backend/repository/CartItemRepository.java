package com.example.backend.repository;

import com.example.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartId(Long cartId);
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /** The lines of a cart WITH their products in ONE query (no lazy product access afterwards), oldest line first. */
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.cart.id = :cartId ORDER BY ci.id")
    List<CartItem> findByCartIdWithProduct(@Param("cartId") Long cartId);

    /** One line with its product and cart, so the owner check and the response need no further query for them. */
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product JOIN FETCH ci.cart WHERE ci.id = :id")
    Optional<CartItem> findByIdWithProductAndCart(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.cart.id = ?1")
    void deleteAllByCartId(Long cartId);
}
