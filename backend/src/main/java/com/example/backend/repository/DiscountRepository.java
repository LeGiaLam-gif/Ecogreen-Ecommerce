package com.example.backend.repository;

import com.example.backend.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    /** Case-insensitive lookup; {@code upperCode} must already be upper-case. Backed by uq_discounts_code_upper (V40). */
    @Query("SELECT d FROM Discount d WHERE upper(d.code) = :upperCode")
    Optional<Discount> findByUpperCode(@Param("upperCode") String upperCode);

    @Query("SELECT count(d) > 0 FROM Discount d WHERE upper(d.code) = :upperCode AND d.id <> :excludeId")
    boolean existsOtherWithUpperCode(@Param("upperCode") String upperCode, @Param("excludeId") Long excludeId);
}
