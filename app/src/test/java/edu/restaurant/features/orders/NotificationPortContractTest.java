package edu.restaurant.features.orders;

import static org.assertj.core.api.Assertions.assertThat;

import edu.restaurant.features.orders.application.NotificationPort;
import edu.restaurant.features.orders.application.OrderCompletedEvent;
import edu.restaurant.features.orders.infrastructure.notification.ConsoleNotificationAdapter;
import edu.restaurant.features.orders.infrastructure.notification.InMemoryNotificationAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NotificationPortContractTest {
    @ParameterizedTest(name = "NotificationPort substitution {0}")
    @MethodSource("implementations")
    void implementationsAcceptACompletedOrderAndAcknowledgeItsIdentity(String name, NotificationPort port) {
        OrderCompletedEvent event = new OrderCompletedEvent(81, 42, new BigDecimal("25.98"), Instant.now(), "corr-81");

        var acknowledgement = port.notifyOrderCompleted(event);

        assertThat(acknowledgement.accepted()).isTrue();
        assertThat(acknowledgement.orderId()).isEqualTo(event.orderId());
        assertThat(acknowledgement.channel()).isNotBlank();
        assertThat(acknowledgement.notifiedAt()).isNotNull();
    }

    static Stream<Arguments> implementations() {
        return Stream.of(
                Arguments.of("console", new ConsoleNotificationAdapter()),
                Arguments.of("in-memory", new InMemoryNotificationAdapter()));
    }
}
