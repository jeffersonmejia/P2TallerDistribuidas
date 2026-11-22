package edu.restaurant.features.demonstrations.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DemoRunResult(String scenario, String name, String concept, String state, String message,
                            Instant startedAt, Instant completedAt, String correlationId, int httpStatus,
                            Map<String, Object> parameters, DemoDatabaseSnapshot before,
                            DemoDatabaseSnapshot after, DemoOrderEvidence orderEvidence,
                            List<DemoHttpCall> httpCalls, DemoSimulatorState simulatorBefore,
                            DemoSimulatorState simulatorAfter, DemoBreakerState breakerBefore,
                            DemoBreakerState breakerAfter, boolean restartRequired) {
    public DemoRunResult {
        parameters = Map.copyOf(parameters);
        httpCalls = List.copyOf(httpCalls);
    }
}
