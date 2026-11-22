package edu.restaurant.features.demonstrations.infrastructure.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.restaurant.features.demonstrations.application.DemoSimulatorControlPort;
import edu.restaurant.features.demonstrations.application.DemoSimulatorState;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("demo")
public class DemoSimulatorControlHttpAdapter implements DemoSimulatorControlPort {
    private final RestClient simulator;
    private final ObjectMapper objectMapper;

    public DemoSimulatorControlHttpAdapter(RestClient.Builder builder, ObjectMapper objectMapper,
                                           @Value("${payment.simulator.base-url}") String baseUrl) {
        simulator = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public DemoSimulatorState state() {
        return call("GET", "/api/control/state", null);
    }

    @Override
    public DemoSimulatorState injectFailures(int count) {
        return call("POST", "/api/control/failures", Map.of("count", count));
    }

    @Override
    public DemoSimulatorState setAvailable(boolean available) {
        return call("PUT", "/api/control/availability", Map.of("available", available));
    }

    private DemoSimulatorState call(String method, String path, Object body) {
        return simulator.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? "" : body)
                .exchange((request, response) -> {
                    String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("El simulador no tiene disponibles los controles de laboratorio (HTTP "
                                + response.getStatusCode().value() + "). Activa PAYMENT_SIMULATOR_DEMO=true y reinícialo.");
                    }
                    return objectMapper.readValue(responseBody, DemoSimulatorState.class);
                });
    }
}
