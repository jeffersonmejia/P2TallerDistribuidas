package edu.restaurant.features.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "Orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer id;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "status_id", nullable = false)
    private Integer statusId;

    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    @Column(name = "table_id")
    private Integer tableId;

    public OrderEntity(LocalDate orderDate, Integer customerId, Integer statusId, String orderType, Integer tableId) {
        this.orderDate = orderDate;
        this.customerId = customerId;
        this.statusId = statusId;
        this.orderType = orderType;
        this.tableId = tableId;
    }

    public Integer getId() { return id; }
    public LocalDate getOrderDate() { return orderDate; }
    public Integer getCustomerId() { return customerId; }
    public Integer getStatusId() { return statusId; }
    public String getOrderType() { return orderType; }
    public Integer getTableId() { return tableId; }
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    protected OrderEntity() {}
}

