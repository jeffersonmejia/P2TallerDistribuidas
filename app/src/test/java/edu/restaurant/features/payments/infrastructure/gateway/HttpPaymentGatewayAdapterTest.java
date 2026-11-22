package edu.restaurant.features.payments.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.restaurant.features.payments.application.PaymentAuthorization;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpPaymentGatewayAdapterTest {
    private static final BigDecimal AMOUNT = new BigDecimal("25.98");

    @Test
    void circuitBreakerBlocksOpenCallsAndTransitionsThroughHalfOpen() throws Exception {
        try (StubSimulator simulator = new StubSimulator()) {
            CircuitBreaker breaker = breaker(2, 2, 50, 180);
            HttpPaymentGatewayAdapter gateway = simulator.gateway(breaker, 500, 500);
            simulator.status.set(503);

            assertThat(gateway.authorize(9, AMOUNT, "op-9").authorized()).isFalse();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(gateway.authorize(9, AMOUNT, "op-9").authorized()).isFalse();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(simulator.calls.get()).isEqualTo(2);

            assertThat(gateway.authorize(9, AMOUNT, "op-9").authorized()).isFalse();
            assertThat(simulator.calls.get()).isEqualTo(2);
            assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);

            simulator.status.set(200);
            awaitState(breaker, CircuitBreaker.State.HALF_OPEN);
            PaymentAuthorization recovered = gateway.authorize(9, AMOUNT, "op-9");
            assertThat(recovered.authorized()).isTrue();
            assertThat(recovered.reference()).isEqualTo("SIM-TEST");
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(simulator.calls.get()).isEqualTo(3);

            simulator.status.set(503);
            gateway.authorize(9, AMOUNT, "op-9");
            gateway.authorize(9, AMOUNT, "op-9");
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            awaitState(breaker, CircuitBreaker.State.HALF_OPEN);
            gateway.authorize(9, AMOUNT, "op-9");
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Test
    void adapterTranslatesInternalRequestAndProviderDecline() throws Exception {
        try (StubSimulator simulator = new StubSimulator()) {
            CircuitBreaker breaker = breaker(4, 2, 50, 1000);
            HttpPaymentGatewayAdapter gateway = simulator.gateway(breaker, 500, 500);
            simulator.status.set(400);

            PaymentAuthorization declined = gateway.authorize(27, AMOUNT, "key-27");

            assertThat(declined.authorized()).isFalse();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(simulator.requestBody.get()).contains("\"value\":25.98", "\"code\":\"ORDER-27\"", "\"operationKey\":\"key-27\"");
        }
    }

    @Test
    void timeoutAndConnectionFailureAreMappedWithoutLeakingExternalErrors() throws Exception {
        try (StubSimulator simulator = new StubSimulator()) {
            CircuitBreaker breaker = breaker(4, 2, 50, 1000);
            HttpPaymentGatewayAdapter gateway = simulator.gateway(breaker, 100, 40);
            simulator.delayMillis.set(250);

            PaymentAuthorization timeout = gateway.authorize(30, AMOUNT, "key-30");
            assertThat(timeout.authorized()).isFalse();
            assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);

            simulator.stopServer();
            PaymentAuthorization unreachable = gateway.authorize(31, AMOUNT, "key-31");
            assertThat(unreachable.authorized()).isFalse();
            assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    private static CircuitBreaker breaker(int window, int minimumCalls, float threshold, long openWaitMillis) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(window)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(threshold)
                .waitDurationInOpenState(Duration.ofMillis(openWaitMillis))
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreaker.of("payment-test-" + System.nanoTime(), config);
    }

    private static void awaitState(CircuitBreaker breaker, CircuitBreaker.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (breaker.getState() != expected && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(breaker.getState()).isEqualTo(expected);
    }

    private static final class StubSimulator implements AutoCloseable {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger status = new AtomicInteger(200);
        private final AtomicLong delayMillis = new AtomicLong();
        private final AtomicReference<String> requestBody = new AtomicReference<>("");
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final HttpServer server;

        private StubSimulator() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/api/transactions", exchange -> {
                calls.incrementAndGet();
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try { Thread.sleep(delayMillis.get()); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                int responseStatus = status.get();
                String body = responseStatus == 200 ? "{\"result\":\"APPROVED\",\"reference\":\"SIM-TEST\"}" : "{\"result\":\"UNAVAILABLE\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(responseStatus, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (IOException ignored) {
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        private HttpPaymentGatewayAdapter gateway(CircuitBreaker breaker, int connectTimeout, int readTimeout) {
            return new HttpPaymentGatewayAdapter(RestClient.builder(), breaker, new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), connectTimeout, readTimeout);
        }

        private void stopServer() {
            server.stop(0);
        }

        @Override public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
