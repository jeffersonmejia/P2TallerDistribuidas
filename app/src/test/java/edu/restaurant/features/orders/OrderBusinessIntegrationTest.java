package edu.restaurant.features.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.restaurant.features.orders.application.OrderApplicationService;
import edu.restaurant.features.orders.application.OrderDetail;
import edu.restaurant.features.orders.application.OrderLineInput;
import edu.restaurant.features.orders.application.OrderPaymentResult;
import edu.restaurant.features.orders.domain.OrderException;
import edu.restaurant.features.orders.domain.OrderType;
import edu.restaurant.features.orders.infrastructure.notification.InMemoryNotificationAdapter;
import edu.restaurant.features.payments.application.PaymentAuthorization;
import edu.restaurant.features.payments.application.PaymentGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.context.annotation.Primary;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(OrderBusinessIntegrationTest.TestGatewayConfiguration.class)
class OrderBusinessIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private OrderApplicationService orders;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CoordinatedGateway gateway;
    @Autowired private TestRestTemplate http;
    @Autowired private InMemoryNotificationAdapter notifications;
    @LocalServerPort private int port;
    private final java.util.Set<Integer> orderIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private int customerId;
    private int itemId;
    private BigDecimal itemPrice;

    @BeforeEach
    void findSeedData() {
        customerId = jdbc.queryForObject("select min(\"customer_id\") from \"Customers\"", Integer.class);
        itemId = jdbc.queryForObject("select min(\"item_id\") from \"Menu_Items\"", Integer.class);
        itemPrice = jdbc.queryForObject("select \"price\" from \"Menu_Items\" where \"item_id\" = ?", BigDecimal.class, itemId);
        gateway.reset();
        notifications.clear();
    }

    @AfterEach
    void deleteCreatedOrders() {
        for (int orderId : orderIds) {
            jdbc.update("delete from \"Payments\" where \"order_id\" = ?", orderId);
            jdbc.update("delete from \"Order_Items\" where \"order_id\" = ?", orderId);
            jdbc.update("delete from \"Orders\" where \"order_id\" = ?", orderId);
        }
    }

    @Test
    void createsEditsAndLogicallyCancelsPendingOrder() {
        OrderDetail created = createOrder(1);
        assertThat(created.status()).isEqualTo("pending");
        assertThat(created.total()).isEqualByComparingTo(itemPrice);

        OrderDetail edited = orders.replaceItems(created.id(), List.of(new OrderLineInput(itemId, 3)));
        assertThat(edited.total()).isEqualByComparingTo(itemPrice.multiply(BigDecimal.valueOf(3)));
        assertThat(jdbc.queryForObject("select count(*) from \"Order_Items\" where \"order_id\" = ?", Integer.class, created.id())).isEqualTo(1);

        orders.cancel(created.id());
        assertThat(orders.get(created.id()).status()).isEqualTo("canceled");
        assertThat(jdbc.queryForObject("select count(*) from \"Orders\" where \"order_id\" = ?", Integer.class, created.id())).isEqualTo(1);
        assertThatThrownBy(() -> orders.replaceItems(created.id(), List.of(new OrderLineInput(itemId, 1))))
                .isInstanceOf(OrderException.class);
    }

    @Test
    void commitsOnePaymentAndRejectsASecondAttempt() {
        OrderDetail order = createOrder(2);
        OrderPaymentResult payment = orders.confirmPayment(order.id(), "credit", key(), false);

        assertThat(payment.status()).isEqualTo("completed");
        assertThat(jdbc.queryForObject("select count(*) from \"Payments\" where \"order_id\" = ?", Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select os.\"status_name\" from \"Orders\" o join \"Order_Status\" os using (\"status_id\") where o.\"order_id\" = ?", String.class, order.id())).isEqualTo("completed");
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"action\"='ORDER_COMPLETED' and \"order_id\"=? and \"result\"='COMMITTED'", Integer.class, order.id())).isEqualTo(1);
        assertThat(notifications.received().stream().filter(event -> event.orderId() == order.id())).hasSize(1);
        assertThatThrownBy(() -> orders.confirmPayment(order.id(), "credit", key(), false))
                .isInstanceOf(OrderException.class).hasMessageContaining("pago confirmado");
        assertThat(jdbc.queryForObject("select count(*) from \"Payments\" where \"order_id\" = ?", Integer.class, order.id())).isEqualTo(1);
    }

    @Test
    void injectedFailureAfterFlushRollsBackPaymentAndCompletionOverHttp() {
        OrderDetail order = createOrder(1);
        String body = "{\"paymentMethod\":\"credit\",\"idempotencyKey\":\"" + key() + "\"}";
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = http.exchange("http://localhost:" + port + "/api/orders/" + order.id() + "/payments?simulateFailure=true",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("INTERNAL_ERROR").doesNotContain("SimulatedPaymentFailure", "stackTrace", "INSERT INTO");
        assertThat(jdbc.queryForObject("select count(*) from \"Payments\" where \"order_id\" = ?", Integer.class, order.id())).isZero();
        assertThat(jdbc.queryForObject("select os.\"status_name\" from \"Orders\" o join \"Order_Status\" os using (\"status_id\") where o.\"order_id\" = ?", String.class, order.id())).isEqualTo("pending");
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"action\"='PAYMENT_ATTEMPT' and \"order_id\"=? and \"result\"='FAILED'", Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"action\"='ORDER_COMPLETED' and \"order_id\"=?", Integer.class, order.id())).isZero();
        assertThat(notifications.received()).isEmpty();
    }

    @Test
    void providerDeclineLeavesOrderPendingWithoutPayment() {
        OrderDetail order = createOrder(1);
        gateway.rejectNext();
        String operationKey = key();
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"paymentMethod\":\"credit\",\"idempotencyKey\":\"" + operationKey + "\"}";
        ResponseEntity<String> response = http.postForEntity("http://localhost:" + port + "/api/orders/" + order.id() + "/payments",
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("PAYMENT_PROVIDER_UNAVAILABLE").doesNotContain("CoordinatedGateway");
        assertThat(jdbc.queryForObject("select count(*) from \"Payments\" where \"order_id\" = ?", Integer.class, order.id())).isZero();
        assertThat(jdbc.queryForObject("select os.\"status_name\" from \"Orders\" o join \"Order_Status\" os using (\"status_id\") where o.\"order_id\" = ?", String.class, order.id())).isEqualTo("pending");
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"action\"='PAYMENT_ATTEMPT' and \"order_id\"=? and \"result\"='REJECTED'", Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"action\"='ORDER_COMPLETED' and \"order_id\"=?", Integer.class, order.id())).isZero();
        assertThat(notifications.received()).isEmpty();
    }

    @Test
    void concurrentPaymentRequestsOnSameOrderProduceOneWinner() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int repetition = 0; repetition < 3; repetition++) {
                OrderDetail order = createOrder(1);
                gateway.synchronizeNextPair();
                Future<Object> first = executor.submit(() -> attemptPayment(order.id()));
                Future<Object> second = executor.submit(() -> attemptPayment(order.id()));
                Object resultA = first.get(20, TimeUnit.SECONDS);
                Object resultB = second.get(20, TimeUnit.SECONDS);
                List<Object> outcomes = List.of(resultA, resultB);
                assertThat(outcomes.stream().filter(OrderPaymentResult.class::isInstance)).hasSize(1);
                assertThat(outcomes.stream().filter(OrderException.class::isInstance).map(OrderException.class::cast)
                        .map(OrderException::reason)).containsExactly(OrderException.Reason.PAYMENT_ALREADY_CONFIRMED);
                assertThat(jdbc.queryForObject("select count(*) from \"Payments\" where \"order_id\" = ?", Integer.class, order.id())).isEqualTo(1);
                assertThat(jdbc.queryForObject("select os.\"status_name\" from \"Orders\" o join \"Order_Status\" os using (\"status_id\") where o.\"order_id\" = ?", String.class, order.id())).isEqualTo("completed");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsPaymentForAnEmptyOrder() {
        OrderDetail order = createOrder(0);
        assertThatThrownBy(() -> orders.confirmPayment(order.id(), "credit", key(), false))
                .isInstanceOf(OrderException.class).hasMessageContaining("al menos un producto");
        assertThat(jdbc.queryForObject("select count(*) from \"Payments\" where \"order_id\" = ?", Integer.class, order.id())).isZero();
    }

    @Test
    void invalidPageParameterReturnsLocalizedClientError() {
        ResponseEntity<String> response = http.getForEntity("http://localhost:" + port + "/api/orders?page=-1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_ERROR", "parámetros").doesNotContain("ConstraintViolationException", "stackTrace");
    }

    @Test
    void httpCrudSerializesOrderDetailsAndKeepsCancellationLogical() throws Exception {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> create = Map.of("customerId", customerId, "orderType", "online",
                "items", List.of(Map.of("itemId", itemId, "quantity", 2)));
        ResponseEntity<String> createdResponse = http.postForEntity(base + "/api/orders", new HttpEntity<>(create, headers), String.class);
        assertThat(createdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdBody = objectMapper.readTree(createdResponse.getBody());
        int orderId = createdBody.get("id").asInt();
        orderIds.add(orderId);
        assertThat(createdBody.get("total").asDouble()).isEqualTo(itemPrice.multiply(BigDecimal.valueOf(2)).doubleValue());

        ResponseEntity<String> detailResponse = http.getForEntity(base + "/api/orders/" + orderId, String.class);
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(detailResponse.getBody()).get("items").size()).isEqualTo(1);

        Map<String, Object> replacement = Map.of("items", List.of(Map.of("itemId", itemId, "quantity", 3)));
        ResponseEntity<String> updated = http.exchange(base + "/api/orders/" + orderId + "/items", HttpMethod.PUT,
                new HttpEntity<>(replacement, headers), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(updated.getBody()).get("total").asDouble()).isEqualTo(itemPrice.multiply(BigDecimal.valueOf(3)).doubleValue());

        ResponseEntity<Void> canceled = http.exchange(base + "/api/orders/" + orderId, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
        assertThat(canceled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(orders.get(orderId).status()).isEqualTo("canceled");
        assertThat(jdbc.queryForObject("select count(*) from \"Orders\" where \"order_id\" = ?", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void catalogAndCustomerEndpointsReturnSeededPostgresData() throws Exception {
        ResponseEntity<String> customers = http.getForEntity("http://localhost:" + port + "/api/customers", String.class);
        ResponseEntity<String> menu = http.getForEntity("http://localhost:" + port + "/api/catalog/menu-items", String.class);
        assertThat(customers.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode customerBody = objectMapper.readTree(customers.getBody());
        assertThat(customerBody.isArray()).isTrue();
        assertThat(customerBody.size()).isPositive();
        assertThat(menu.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode menuBody = objectMapper.readTree(menu.getBody());
        assertThat(menuBody.isArray()).isTrue();
        assertThat(menuBody.size()).isPositive();
    }

    @Test
    void paymentProxyPersistsAttemptAndBothObserversShareTheCorrelationId() {
        OrderDetail order = createOrder(1);
        String correlationId = "stage3-correlation-" + UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", correlationId);
        String body = "{\"paymentMethod\":\"credit\",\"idempotencyKey\":\"" + key() + "\"}";

        ResponseEntity<String> response = http.postForEntity("http://localhost:" + port + "/api/orders/" + order.id() + "/payments",
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst("X-Correlation-ID")).isEqualTo(correlationId);
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"action\"='PAYMENT_ATTEMPT' and \"order_id\"=? and \"result\"='CONFIRMED' and \"actor\"='DEMO_OPERATOR' and \"correlation_id\"=?", Integer.class, order.id(), correlationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"action\"='ORDER_COMPLETED' and \"order_id\"=? and \"result\"='COMMITTED' and \"correlation_id\"=?", Integer.class, order.id(), correlationId)).isEqualTo(1);
        assertThat(notifications.received().stream().filter(event -> event.orderId() == order.id())
                .map(event -> event.correlationId())).containsExactly(correlationId);
    }

    private OrderDetail createOrder(int quantity) {
        OrderDetail created = orders.create(customerId, null, OrderType.ONLINE,
                quantity == 0 ? List.of() : List.of(new OrderLineInput(itemId, quantity)));
        orderIds.add(created.id());
        return created;
    }

    private Object attemptPayment(int id) {
        try { return orders.confirmPayment(id, "credit", key(), false); }
        catch (OrderException exception) { return exception; }
    }

    private static String key() { return "order-it-" + UUID.randomUUID(); }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestGatewayConfiguration {
        @Bean @Primary CoordinatedGateway coordinatedGateway() { return new CoordinatedGateway(); }
    }

    static final class CoordinatedGateway implements PaymentGateway {
        private final AtomicBoolean reject = new AtomicBoolean();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile CyclicBarrier barrier;
        void reset() { reject.set(false); calls.set(0); barrier = null; }
        void rejectNext() { reject.set(true); }
        void synchronizeNextPair() { barrier = new CyclicBarrier(2); }
        @Override public PaymentAuthorization authorize(int orderId, BigDecimal amount, String idempotencyKey) {
            calls.incrementAndGet();
            CyclicBarrier current = barrier;
            if (current != null) {
                try { current.await(10, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException("No se sincronizaron las autorizaciones concurrentes", exception); }
            }
            boolean authorized = !reject.getAndSet(false);
            return new PaymentAuthorization(authorized, amount, "TEST-" + idempotencyKey, Instant.now());
        }
    }
}
