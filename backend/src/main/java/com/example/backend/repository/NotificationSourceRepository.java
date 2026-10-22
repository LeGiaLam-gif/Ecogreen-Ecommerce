package com.example.backend.repository;

import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * READ-ONLY lookups for B10: the data the published events do not carry (the customer's contact, the owner and total of an
 * order, the owner and note of a return). Projections only, so nothing is lazy-loaded outside a session and no other
 * module's repository is touched. It extends {@link Repository}: no save or delete method exists on it.
 */
public interface NotificationSourceRepository extends Repository<User, Long> {

    interface ContactRow {
        Long getId();

        String getUsername();

        String getEmail();

        Boolean getActive();
    }

    interface OrderRow {
        Long getUserId();

        BigDecimal getTotalPrice();
    }

    interface ReturnRow {
        Long getUserId();

        Long getOrderId();

        String getAdminNote();
    }

    @Query("select u.id as id, u.username as username, u.email as email, u.active as active from User u where u.id = :id")
    Optional<ContactRow> findContact(@Param("id") Long id);

    @Query("select o.user.id as userId, o.totalPrice as totalPrice from Order o where o.id = :id")
    Optional<OrderRow> findOrder(@Param("id") Long id);

    @Query("select r.user.id as userId, r.order.id as orderId, r.adminNote as adminNote from ReturnRequest r where r.id = :id")
    Optional<ReturnRow> findReturn(@Param("id") Long id);
}
