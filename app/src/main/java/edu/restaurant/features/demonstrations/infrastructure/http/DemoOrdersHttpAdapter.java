package edu.restaurant.features.demonstrations.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.restaurant.features.demonstrations.application.DemoHttpCall;
import edu.restaurant.features.demonstrations.application.DemoOrdersHttpPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("demo")
public class DemoOrdersHttpAdapter implements DemoOrdersHttpPort {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private final Environment environment;
    private final ObjectMapper objectMapper;

    public DemoOrdersHttpAdapter(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @Override
    public DemoHttpCall create(int customerId, int itemId, int quantity, String correlationId) {
        return exchange("POST", "/api/orders", Map.of("customerId", customerId, "orderType", "online",
                "items", List.of(Map.of("itemId", itemId, "quantity", quantity))), correlationId);
    }

    @Override
    public DemoHttpCall get(int orderId, String correlationId) {
        return exchange("GET", "/api/orders/" + orderId, null, correlationId);
    }

    @Override
    public DemoHttpCall replaceItems(int orderId, int itemId, int quantity, String correlationId) {
        return exchange("PUT", "/api/orders/" + orderId + "/items",
                Map.of("items", List.of(Map.of("itemId", itemId, "quantity", quantity))), correlationId);
    }

    @Override
    public DemoHttpCall cancel(int orderId, String correlationId) {
        return exchange("DELETE", "/api/orders/" + orderId, null, correlationId);
    }

    @Override
    public DemoHttpCall pay(int orderId, String operationKey, boolean simulateFailure, String correlationId) {
        String path = "/api/orders/" + orderId + "/payments" + (simulateFailure ? "?simulateFailure=true" : "");
        return exchange("POST", path, Map.of("paymentMethod", "credit", "idempotencyKey", operationKey), correlationId);
    }

    @Override
    public DemoHttpCall payWithInvalidQuantity(int customerId, int itemId, String correlationId) {
        return exchange("POST", "/api/orders", Map.of("customerId", customerId, "orderType", "online",
                "items", List.of(Map.of("itemId", itemId, "quantity", 0))), correlationId);
    }

    @Override
    public List<DemoHttpCall> payConcurrently(int orderId, String operationPrefix, String correlationPrefix) {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<DemoHttpCall> first = executor.submit(() -> concurrentPayment(orderId,
                    operationPrefix + "-A", correlationPrefix + "-A", ready, start));
            Future<DemoHttpCall> second = executor.submit(() -> concurrentPayment(orderId,
                    operationPrefix + "-B", correlationPrefix + "-B", ready, start));
            if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Las dos solicitudes de pago no se prepararon a tiempo.");
            }
            start.countDown();
            return List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo coordinar la concurrencia HTTP del laboratorio.", exception);
        }
    }

    @Override
    public int orderIdFrom(DemoHttpCall call) {
        if (call.status() != 201) throw new IllegalStateException("La API no creó la orden del escenario.");
        try {
            JsonNode body = objectMapper.readTree(call.body());
            return body.path("id").asInt(0);
        } catch (Exception exception) {
            throw new IllegalStateException("La respuesta de creación no contiene un identificador válido.", exception);
        }
    }

    private DemoHttpCall concurrentPayment(int orderId, String key, String correlationId,
                                           CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("La barrera de inicio de concurrencia expiró.");
        }
        return pay(orderId, key, false, correlationId);
    }

    private DemoHttpCall exchange(String method, String path, Object body, String correlationId) {
        RestClient client = RestClient.builder().baseUrl(baseUrl()).build();
        return client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-Correlation-ID", correlationId))
                .body(body == null ? "" : body)
                .exchange((request, response) -> new DemoHttpCall(method, path, response.getStatusCode().value(),
                        response.getHeaders().getFirst("X-Correlation-ID"),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private String baseUrl() {
        int port = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, 8080));
        return "http://127.0.0.1:" + port;
    }
}
