package edu.restaurant.features.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Order_Items")
public class OrderItemEntity {
    @EmbeddedId
    private OrderItemId id;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    protected OrderItemEntity() {}

    public OrderItemEntity(int orderId, int itemId, int quantity) {
        this.id = new OrderItemId(orderId, itemId);
        this.quantity = quantity;
    }

    public OrderItemId getId() { return id; }
    public Integer getQuantity() { return quantity; }
}
