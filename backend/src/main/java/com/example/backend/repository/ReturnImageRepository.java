package com.example.backend.repository;

import com.example.backend.entity.ReturnImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReturnImageRepository extends JpaRepository<ReturnImage, Long> {

    /** The images of many returns in ONE query, in display order (served by idx_return_images_return). */
    @Query("SELECT i FROM ReturnImage i WHERE i.returnRequest.id IN :returnIds ORDER BY i.sortOrder, i.id")
    List<ReturnImage> findByReturnIdIn(@Param("returnIds") Collection<Long> returnIds);
}
