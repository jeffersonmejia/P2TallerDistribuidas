package edu.restaurant.features.payments.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.restaurant.features.payments.application.PaymentAuthorization;
import edu.restaurant.features.payments.application.PaymentGateway;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test")
public class HttpPaymentGatewayAdapter implements PaymentGateway {
    private static final Logger logger = LoggerFactory.getLogger(HttpPaymentGatewayAdapter.class);
    private final RestClient client;
    private final CircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;

    public HttpPaymentGatewayAdapter(RestClient.Builder builder, CircuitBreaker circuitBreaker, ObjectMapper objectMapper,
                                    @Value("${payment.simulator.base-url}") String baseUrl,
                                    @Value("${payment.simulator.connect-timeout-ms}") int connectTimeoutMillis,
                                    @Value("${payment.simulator.read-timeout-ms}") int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentAuthorization authorize(int orderId, BigDecimal amount, String idempotencyKey) {
        try {
            return circuitBreaker.executeSupplier(() -> authorizeRemote(orderId, amount, idempotencyKey));
        } catch (CallNotPermittedException exception) {
            logger.warn("Circuit breaker rechazó la autorización antes de contactar al simulador orderId={}", orderId);
            return declined(amount);
        } catch (RuntimeException exception) {
            logger.warn("No se pudo autorizar el pago simulado orderId={} class={}", orderId, exception.getClass().getSimpleName());
            return declined(amount);
        }
    }

    private PaymentAuthorization authorizeRemote(int orderId, BigDecimal amount, String idempotencyKey) {
        SimulatorRequest body = new SimulatorRequest(amount, "ORDER-" + orderId, idempotencyKey);
        return client.post().uri("/api/transactions").contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is5xxServerError() || status.value() == 429) {
                        throw new SimulatorUnavailableException(status.value());
                    }
                    if (status.is4xxClientError()) return declined(amount);
                    try {
                        SimulatorResponse result = objectMapper.readValue(response.getBody(), SimulatorResponse.class);
                        if ("APPROVED".equals(result.result()) && result.reference() != null && !result.reference().isBlank()) {
                            return new PaymentAuthorization(true, amount, result.reference(), Instant.now());
                        }
                        if ("DECLINED".equals(result.result())) return declined(amount);
                        throw new IllegalStateException("Respuesta no reconocida del simulador");
                    } catch (IOException exception) {
                        throw new IllegalStateException("No se pudo interpretar la respuesta del simulador", exception);
                    }
                });
    }

    private static PaymentAuthorization declined(BigDecimal amount) {
        return new PaymentAuthorization(false, amount, "", Instant.now());
    }

    private record SimulatorRequest(BigDecimal value, String code, String operationKey) {}
    private record SimulatorResponse(String result, String reference, String message) {}

    private static final class SimulatorUnavailableException extends RuntimeException {
        private SimulatorUnavailableException(int status) {
            super("El simulador respondió con estado HTTP " + status);
        }
    }
}
