package edu.restaurant.features.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import edu.restaurant.features.orders.application.OrderApplicationService;
import edu.restaurant.features.orders.application.OrderDetail;
import edu.restaurant.features.orders.application.OrderLineInput;
import edu.restaurant.features.orders.domain.OrderType;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-http")
class PaymentFlowHttpIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final StubSimulator SIMULATOR = new StubSimulator();
    static {
        POSTGRES.start();
        SIMULATOR.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("payment.simulator.base-url", () -> SIMULATOR.url());
        registry.add("payment.circuit-breaker.sliding-window-size", () -> 4);
        registry.add("payment.circuit-breaker.minimum-calls", () -> 2);
        registry.add("payment.circuit-breaker.failure-threshold", () -> 50);
        registry.add("payment.circuit-breaker.open-wait-ms", () -> 2000);
        registry.add("payment.circuit-breaker.half-open-calls", () -> 1);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderApplicationService orders;
    @Autowired private TestRestTemplate http;
    @LocalServerPort private int port;
    private OrderDetail order;

    @BeforeEach
    void createPendingOrder() {
        SIMULATOR.reset();
        Integer customerId = jdbc.queryForObject("select min(\"customer_id\") from \"Customers\"", Integer.class);
        Integer itemId = jdbc.queryForObject("select min(\"item_id\") from \"Menu_Items\"", Integer.class);
        order = orders.create(customerId, null, OrderType.ONLINE, List.of(new OrderLineInput(itemId, 1)));
    }

    @AfterAll
    static void stopSimulator() {
        SIMULATOR.close();
    }

    @Test
    void providerFailureThenIdempotentRetryCompletesOnceAndPersistsSeparatedAudit() {
        String operationKey = "http-flow-" + UUID.randomUUID();
        String correlation = "external-flow-" + UUID.randomUUID();
        SIMULATOR.status.set(503);
        ResponseEntity<String> unavailable = postPayment(operationKey, correlation);

        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(jdbc.queryForObject("select count(*) from \"Payments\" where \"order_id\"=?", Integer.class, order.id())).isZero();
        assertThat(status()).isEqualTo("pending");

        SIMULATOR.status.set(200);
        ResponseEntity<String> confirmed = postPayment(operationKey, correlation);

        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(confirmed.getBody()).contains("\"status\":\"completed\"");
        assertThat(SIMULATOR.calls.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from \"Payments\" where \"order_id\"=?", Integer.class, order.id())).isEqualTo(1);
        assertThat(status()).isEqualTo("completed");
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"order_id\"=? and \"action\"='PAYMENT_ATTEMPT' and \"result\"='REJECTED' and \"operation_key\"=?", Integer.class, order.id(), operationKey)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"order_id\"=? and \"action\"='PAYMENT_ATTEMPT' and \"result\"='CONFIRMED' and \"operation_key\"=? and \"correlation_id\"=?", Integer.class, order.id(), operationKey, correlation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from \"Audit_Events\" where \"order_id\"=? and \"action\"='ORDER_COMPLETED' and \"result\"='COMMITTED'", Integer.class, order.id())).isEqualTo(1);
    }

    private ResponseEntity<String> postPayment(String operationKey, String correlation) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", correlation);
        String body = "{\"paymentMethod\":\"credit\",\"idempotencyKey\":\"" + operationKey + "\"}";
        return http.postForEntity("http://localhost:" + port + "/api/orders/" + order.id() + "/payments",
                new HttpEntity<>(body, headers), String.class);
    }

    private String status() {
        return jdbc.queryForObject("select os.\"status_name\" from \"Orders\" o join \"Order_Status\" os using (\"status_id\") where o.\"order_id\"=?", String.class, order.id());
    }

    private static final class StubSimulator implements AutoCloseable {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<Integer> status = new AtomicReference<>(200);
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final HttpServer server;

        private StubSimulator() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.setExecutor(executor);
                server.createContext("/api/transactions", exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    calls.incrementAndGet();
                    int responseStatus = status.get();
                    String body = responseStatus == 200 ? "{\"result\":\"APPROVED\",\"reference\":\"SIM-E2E\"}" : "{\"result\":\"UNAVAILABLE\"}";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(responseStatus, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
            } catch (IOException exception) {
                throw new IllegalStateException("No se pudo crear el simulador HTTP de prueba", exception);
            }
        }

        private void start() { server.start(); }
        private String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        private void reset() { calls.set(0); status.set(200); }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }
}
