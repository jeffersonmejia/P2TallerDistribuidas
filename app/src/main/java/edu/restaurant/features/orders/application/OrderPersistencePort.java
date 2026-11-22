package edu.restaurant.features.orders.application;

import edu.restaurant.features.orders.domain.OrderAggregate;
import edu.restaurant.features.orders.domain.OrderItem;
import edu.restaurant.features.orders.domain.OrderStatus;
import java.util.List;
import java.util.Optional;

public interface OrderPersistencePort {
    Optional<OrderAggregate> find(int orderId);
    Optional<OrderAggregate> findForUpdate(int orderId);
    boolean tableExists(int tableId);
    int statusId(OrderStatus status);
    int create(OrderAggregate order, int pendingStatusId);
    void replaceItems(int orderId, List<OrderItem> items);
    void changeStatus(int orderId, int statusId);
}
