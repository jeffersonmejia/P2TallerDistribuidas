package edu.restaurant.features.orders.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select order from OrderEntity order where order.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") int id);
}
