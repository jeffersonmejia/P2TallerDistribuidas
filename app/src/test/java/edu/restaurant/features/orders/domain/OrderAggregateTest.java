package edu.restaurant.features.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderAggregateTest {
    @Test
    void pendingOrderCanReplaceItemsAndCancel() {
        OrderAggregate order = OrderAggregate.pending(1, null, OrderType.ONLINE, List.of(new OrderItem(1, 2)));
        assertThat(order.withItems(List.of(new OrderItem(2, 1))).items()).containsExactly(new OrderItem(2, 1));
        assertThat(order.cancel().status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void completedOrderCannotBeEditedOrCanceled() {
        OrderAggregate completed = new OrderAggregate(1, java.time.LocalDate.now(), 1, null, OrderType.ONLINE,
                OrderStatus.COMPLETED, List.of(new OrderItem(1, 1)));
        assertThatThrownBy(() -> completed.withItems(List.of())).isInstanceOf(OrderException.class);
        assertThatThrownBy(completed::cancel).isInstanceOf(OrderException.class);
    }

    @Test
    void paymentRequiresAnItemAndPositiveQuantity() {
        OrderAggregate empty = OrderAggregate.pending(1, null, OrderType.ONLINE, List.of());
        assertThatThrownBy(empty::ensureCanPay).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> new OrderItem(1, 0)).isInstanceOf(OrderException.class);
    }
}
