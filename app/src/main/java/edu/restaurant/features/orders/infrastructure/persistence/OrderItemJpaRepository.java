package edu.restaurant.features.orders.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, OrderItemId> {
    @Query("select item from OrderItemEntity item where item.id.orderId = :orderId order by item.id.itemId")
    List<OrderItemEntity> findAllByOrderId(@Param("orderId") int orderId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OrderItemEntity item where item.id.orderId = :orderId")
    void deleteAllByOrderId(@Param("orderId") int orderId);
}
