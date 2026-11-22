package edu.restaurant.features.orders.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusJpaRepository extends JpaRepository<OrderStatusEntity, Integer> {
    Optional<OrderStatusEntity> findByName(String name);
}
