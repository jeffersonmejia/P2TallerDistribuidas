package edu.restaurant.features.orders.infrastructure.persistence;

import edu.restaurant.features.orders.application.OrderPersistencePort;
import edu.restaurant.features.orders.domain.OrderAggregate;
import edu.restaurant.features.orders.domain.OrderItem;
import edu.restaurant.features.orders.domain.OrderStatus;
import edu.restaurant.features.orders.domain.OrderType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderPersistenceAdapter implements OrderPersistencePort {
    private final OrderJpaRepository orders;
    private final OrderItemJpaRepository items;
    private final OrderStatusJpaRepository statuses;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public OrderPersistenceAdapter(OrderJpaRepository orders, OrderItemJpaRepository items, OrderStatusJpaRepository statuses,
                                   JdbcTemplate jdbc, EntityManager entityManager) {
        this.orders = orders;
        this.items = items;
        this.statuses = statuses;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<OrderAggregate> find(int orderId) {
        return orders.findById(orderId).map(this::toDomain);
    }

    @Override
    public Optional<OrderAggregate> findForUpdate(int orderId) {
        return orders.findByIdForUpdate(orderId).map(this::toDomain);
    }

    @Override
    public boolean tableExists(int tableId) {
        Boolean exists = jdbc.queryForObject("select exists(select 1 from \"Guest_Tables\" where \"table_id\" = ?)", Boolean.class, tableId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public int statusId(OrderStatus status) {
        return statuses.findByName(status.databaseValue()).map(OrderStatusEntity::getId)
                .orElseThrow(() -> new IllegalStateException("No existe el estado requerido en el catálogo original"));
    }

    @Override
    public int create(OrderAggregate order, int pendingStatusId) {
        OrderEntity entity = orders.saveAndFlush(new OrderEntity(order.orderDate(), order.customerId(), pendingStatusId,
                order.type().databaseValue(), order.tableId()));
        saveItems(entity.getId(), order.items());
        return entity.getId();
    }

    @Override
    public void replaceItems(int orderId, List<OrderItem> replacement) {
        items.deleteAllByOrderId(orderId);
        items.flush();
        saveItems(orderId, replacement);
    }

    @Override
    public void changeStatus(int orderId, int statusId) {
        OrderEntity entity = orders.findById(orderId).orElseThrow();
        entity.setStatusId(statusId);
        orders.saveAndFlush(entity);
    }

    private void saveItems(int orderId, List<OrderItem> lines) {
        if (lines.isEmpty()) return;
        lines.stream().map(line -> new OrderItemEntity(orderId, line.menuItemId(), line.quantity())).forEach(entityManager::persist);
        entityManager.flush();
    }

    private OrderAggregate toDomain(OrderEntity entity) {
        String statusName = statuses.findById(entity.getStatusId()).orElseThrow().getName();
        List<OrderItem> lines = items.findAllByOrderId(entity.getId()).stream()
                .map(item -> new OrderItem(item.getId().getItemId(), item.getQuantity())).toList();
        return new OrderAggregate(entity.getId(), entity.getOrderDate(), entity.getCustomerId(), entity.getTableId(),
                OrderType.fromDatabase(entity.getOrderType()), OrderStatus.fromDatabase(statusName), lines);
    }
}
