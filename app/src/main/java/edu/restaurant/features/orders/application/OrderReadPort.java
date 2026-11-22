package edu.restaurant.features.orders.application;

import java.util.Optional;

public interface OrderReadPort {
    OrderPage search(String term, int page, int size);
    Optional<OrderDetail> detail(int orderId);
}
