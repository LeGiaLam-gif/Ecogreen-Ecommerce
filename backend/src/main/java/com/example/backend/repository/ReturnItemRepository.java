package com.example.backend.repository;

import com.example.backend.entity.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    /** The items of many returns with their order line and product in ONE query (a page of returns needs no query per row). */
    @Query("SELECT ri FROM ReturnItem ri JOIN FETCH ri.orderItem oi JOIN FETCH oi.product "
            + "WHERE ri.returnRequest.id IN :returnIds ORDER BY ri.id")
    List<ReturnItem> findByReturnIdInWithProduct(@Param("returnIds") Collection<Long> returnIds);
}
