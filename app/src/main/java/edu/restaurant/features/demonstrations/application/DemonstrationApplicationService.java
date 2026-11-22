package edu.restaurant.features.demonstrations.application;

import edu.restaurant.features.orders.application.FinalizeOrderPayment;
import edu.restaurant.features.orders.domain.OrderException;
import edu.restaurant.features.payments.application.PaymentAuthorization;
import edu.restaurant.features.payments.application.PaymentGateway;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("demo")
public class DemonstrationApplicationService {
    private static final List<DemoScenario> SCENARIOS = List.of(
            new DemoScenario("crud", "CRUD de órdenes", "Persistencia y estados",
                    "Crea, consulta, edita y cancela lógicamente una orden; compara las filas persistidas."),
            new DemoScenario("commit", "COMMIT de pago", "Atomicidad y durabilidad",
                    "Confirma un pago simulado y verifica orden, detalle, pago y evento persistidos."),
            new DemoScenario("rollback", "ROLLBACK después de escrituras", "Atomicidad",
                    "Inyecta una excepción después del flush y comprueba que pago y estado no cambien."),
            new DemoScenario("consistency", "Reglas de consistencia", "Integridad y validación",
                    "Rechaza cantidad cero, orden inexistente e importe autorizado incoherente."),
            new DemoScenario("concurrency", "Pago concurrente", "Aislamiento y bloqueo pesimista",
                    "Envía dos solicitudes HTTP simultáneas; una confirma y la otra recibe conflicto."),
            new DemoScenario("durability", "Durabilidad", "ACID: durabilidad",
                    "Confirma una orden etiquetada. Reinicia backend y PostgreSQL sin borrar el volumen y luego verifica su ID."),
            new DemoScenario("verify-durability", "Verificar durabilidad", "ACID: durabilidad",
                    "Consulta PostgreSQL después del reinicio y confirma la persistencia de la orden, pago y evento."),
            new DemoScenario("proxy", "Proxy de pago", "Proxy y auditoría separada",
                    "Comprueba delegación HTTP, actor DEMO_OPERATOR, clave de operación y correlación."),
            new DemoScenario("observer", "Observadores post-commit", "Observer y transacciones",
                    "Comprueba el evento persistido después del commit y la traza de notificación en logs del backend."),
            new DemoScenario("adapter", "Adapter del proveedor", "Adapter HTTP",
                    "Traduce una autorización interna al contrato distinto del simulador sin insertar un pago."),
            new DemoScenario("breaker-open", "Circuit Breaker: abrir y bloquear", "Resiliencia",
                    "Inyecta fallos reales, observa OPEN y confirma que las llamadas bloqueadas no llegan al simulador."),
            new DemoScenario("breaker-recovery", "Circuit Breaker: recuperar", "Resiliencia",
                    "Espera HALF_OPEN y deja que una autorización exitosa cierre el breaker."),
            new DemoScenario("breaker-reopen", "Circuit Breaker: fallo en HALF_OPEN", "Resiliencia",
                    "Abre el breaker y falla la prueba HALF_OPEN para comprobar el regreso a OPEN."));

    private final DemoEvidencePort evidence;
    private final DemoOrdersHttpPort ordersHttp;
    private final DemoSimulatorControlPort simulator;
    private final FinalizeOrderPayment finalizer;
    private final PaymentGateway paymentGateway;
    private final CircuitBreaker circuitBreaker;
    private final int breakerMinimumCalls;
    private final long breakerOpenWaitMillis;

    public DemonstrationApplicationService(DemoEvidencePort evidence, DemoOrdersHttpPort ordersHttp,
                                           DemoSimulatorControlPort simulator, FinalizeOrderPayment finalizer,
                                           PaymentGateway paymentGateway,
                                           CircuitBreaker circuitBreaker,
                                           @Value("${payment.circuit-breaker.minimum-calls}") int breakerMinimumCalls,
                                           @Value("${payment.circuit-breaker.open-wait-ms}") long breakerOpenWaitMillis) {
        this.evidence = evidence;
        this.ordersHttp = ordersHttp;
        this.simulator = simulator;
        this.finalizer = finalizer;
        this.paymentGateway = paymentGateway;
        this.circuitBreaker = circuitBreaker;
        this.breakerMinimumCalls = breakerMinimumCalls;
        this.breakerOpenWaitMillis = breakerOpenWaitMillis;
    }

    public List<DemoScenario> scenarios() {
        return SCENARIOS;
    }

    public DemoLabStatus status() {
        return new DemoLabStatus(true, simulatorState(), breakerState(), evidence.snapshot());
    }

    public DemoRunResult run(String scenario, DemoRunRequest request) {
        String id = UUID.randomUUID().toString();
        String correlationId = "LAB04-" + id;
        Instant startedAt = Instant.now();
        DemoScenario definition = SCENARIOS.stream().filter(item -> item.id().equals(scenario)).findFirst()
                .orElseThrow(() -> OrderException.validation("El escenario indicado no está disponible."));
        DemoDatabaseSnapshot before = evidence.snapshot();
        DemoSimulatorState simulatorBefore = simulatorState();
        DemoBreakerState breakerBefore = breakerState();
        ScenarioTrace trace = new ScenarioTrace();
        DemoOrderEvidence orderEvidence = null;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("operationPrefix", "LAB04-" + scenario.toUpperCase() + "-" + id.substring(0, 8));
        parameters.put("demoOperator", "DEMO_OPERATOR");
        String state = "VERIFIED";
        String message;
        int httpStatus = 200;
        boolean restartRequired = false;

        switch (scenario) {
            case "crud" -> {
                DemoSeedData seed = evidence.seedData();
                DemoHttpCall created = create(seed, correlationId, trace);
                int orderId = ordersHttp.orderIdFrom(created);
                parameters.put("orderId", orderId);
                trace.add(ordersHttp.get(orderId, correlationId));
                trace.add(ordersHttp.replaceItems(orderId, seed.menuItemId(), 2, correlationId));
                trace.add(ordersHttp.cancel(orderId, correlationId));
                orderEvidence = evidence.order(orderId);
                message = "La orden conserva sus filas, el detalle quedó actualizado y el estado final es cancelled.";
                if (!"canceled".equals(orderEvidence.status()) || orderEvidence.paymentCount() != 0) state = "FAILED";
            }
            case "commit" -> {
                orderEvidence = runPayment(seedAndCreate(correlationId, trace, parameters),
                        "COMMIT", false, correlationId, trace);
                message = orderEvidence != null && orderEvidence.paymentCount() == 1
                        && "completed".equals(orderEvidence.status())
                        ? "El commit dejó una orden completada, un pago y un evento durable en PostgreSQL."
                        : "El escenario no confirmó exactamente un pago; revisa la respuesta HTTP y el estado persistido.";
                if (orderEvidence == null || orderEvidence.paymentCount() != 1 || !"completed".equals(orderEvidence.status())) state = "FAILED";
                if (orderEvidence != null) parameters.put("orderId", orderEvidence.orderId());
            }
            case "rollback" -> {
                int orderId = seedAndCreate(correlationId, trace, parameters);
                trace.add(ordersHttp.pay(orderId, operationKey("ROLLBACK", id), true, correlationId));
                orderEvidence = evidence.order(orderId);
                parameters.put("orderId", orderId);
                message = "La API devolvió el error inyectado; PostgreSQL conserva la orden pendiente y cero pagos/eventos de confirmación.";
                if (!"pending".equals(orderEvidence.status()) || orderEvidence.paymentCount() != 0 || orderEvidence.completedEventCount() != 0) state = "FAILED";
            }
            case "consistency" -> {
                DemoSeedData seed = evidence.seedData();
                trace.add(ordersHttp.payWithInvalidQuantity(seed.customerId(), seed.menuItemId(), correlationId + "-QTY"));
                int missingId = Integer.MAX_VALUE - 5;
                trace.add(ordersHttp.pay(missingId, operationKey("MISSING", id), false, correlationId + "-MISSING"));
                int orderId = seedAndCreate(correlationId + "-AMOUNT", trace, parameters);
                try {
                    finalizer.finish(orderId, "credit", seed.unitPrice().add(new BigDecimal("0.01")), false);
                    state = "FAILED";
                } catch (OrderException exception) {
                    trace.add(new DemoHttpCall("APP", "FinalizeOrderPayment.finish", 409,
                            correlationId + "-AMOUNT", exception.getMessage()));
                }
                orderEvidence = evidence.order(orderId);
                parameters.put("nonexistentOrderId", missingId);
                parameters.put("authorizedAmount", seed.unitPrice().add(new BigDecimal("0.01")));
                parameters.put("orderId", orderId);
                message = "La API rechazó cantidad cero y orden inexistente; el caso de autorización con importe distinto quedó en conflicto, sin guardar pago.";
                if (orderEvidence.paymentCount() != 0 || !"pending".equals(orderEvidence.status())) state = "FAILED";
            }
            case "concurrency" -> {
                DemoSeedData seed = evidence.seedData();
                int orderId = ordersHttp.orderIdFrom(create(seed, correlationId, trace));
                parameters.put("orderId", orderId);
                List<DemoHttpCall> concurrent = ordersHttp.payConcurrently(orderId,
                        operationKey("CONCURRENT", id), correlationId);
                trace.addAll(concurrent);
                orderEvidence = evidence.order(orderId);
                long created = concurrent.stream().filter(call -> call.status() == 201).count();
                long conflicts = concurrent.stream().filter(call -> call.status() == 409).count();
                message = "Dos solicitudes HTTP reales compitieron por la misma orden; PostgreSQL guardó un pago y la otra solicitud recibió conflicto.";
                if (created != 1 || conflicts != 1 || orderEvidence.paymentCount() != 1) state = "FAILED";
            }
            case "durability" -> {
                int orderId = seedAndCreate(correlationId, trace, parameters);
                trace.add(ordersHttp.pay(orderId, operationKey("DURABILITY", id), false, correlationId));
                orderEvidence = evidence.order(orderId);
                parameters.put("orderId", orderId);
                restartRequired = true;
                message = "Orden etiquetada con una clave LAB04. Reinicia el backend y PostgreSQL sin usar `docker compose down -v`; después pulsa Verificar con este ID.";
                if (orderEvidence.paymentCount() != 1 || !"completed".equals(orderEvidence.status())) state = "FAILED";
            }
            case "verify-durability" -> {
                Integer orderId = request == null ? null : request.orderId();
                if (orderId == null || orderId < 1) throw OrderException.validation("Indica el ID de la orden después del reinicio.");
                parameters.put("orderId", orderId);
                orderEvidence = evidence.order(orderId);
                message = "La consulta SQL posterior al reinicio encontró la orden, su pago y el evento de commit.";
                if (!orderEvidence.exists() || orderEvidence.paymentCount() != 1 || !"completed".equals(orderEvidence.status())) state = "FAILED";
            }
            case "proxy" -> {
                int orderId = seedAndCreate(correlationId, trace, parameters);
                trace.add(ordersHttp.pay(orderId, operationKey("PROXY", id), false, correlationId));
                orderEvidence = evidence.order(orderId);
                parameters.put("orderId", orderId);
                message = "El Proxy recibió la solicitud HTTP y persistió el intento con actor, clave y correlación antes de responder.";
                if (orderEvidence.paymentAttemptCount() != 1 || !"DEMO_OPERATOR".equals(orderEvidence.lastAttemptActor())
                        || !correlationId.equals(orderEvidence.lastAttemptCorrelationId())) state = "FAILED";
            }
            case "observer" -> {
                int orderId = seedAndCreate(correlationId, trace, parameters);
                trace.add(ordersHttp.pay(orderId, operationKey("OBSERVER", id), false, correlationId));
                orderEvidence = evidence.order(orderId);
                parameters.put("orderId", orderId);
                message = "AuditObserver confirmó un evento COMMITTED con la correlación indicada; NotificationObserver dejó su traza en la consola backend.";
                if (orderEvidence.completedEventCount() != 1 || !correlationId.equals(orderEvidence.lastCompletedCorrelationId())) state = "FAILED";
            }
            case "adapter" -> {
                DemoSeedData seed = evidence.seedData();
                PaymentAuthorization authorization = paymentGateway.authorize(2_147_000_000,
                        seed.unitPrice(), operationKey("ADAPTER", id));
                parameters.put("internalContract", "authorize(orderId, amount, idempotencyKey)");
                parameters.put("externalContract", "executeTransaction(value, code, operationKey)");
                parameters.put("authorized", authorization.authorized());
                parameters.put("reference", authorization.reference());
                message = authorization.authorized()
                        ? "El Adapter llamó al proveedor HTTP, recibió una referencia SIM y no insertó un pago en PostgreSQL."
                        : "El Adapter convirtió la respuesta o fallo del proveedor a una autorización rechazada; no insertó pago.";
            }
            case "breaker-open" -> message = openBreaker(id, parameters);
            case "breaker-recovery" -> message = recoverBreaker(id, parameters, true);
            case "breaker-reopen" -> message = recoverBreaker(id, parameters, false);
            default -> throw OrderException.validation("El escenario indicado no está disponible.");
        }

        DemoDatabaseSnapshot after = evidence.snapshot();
        if (orderEvidence != null && orderEvidence.exists()) orderEvidence = evidence.order(orderEvidence.orderId());
        return new DemoRunResult(scenario, definition.name(), definition.concept(), state, message,
                startedAt, Instant.now(), correlationId, httpStatus, parameters, before, after, orderEvidence,
                trace.calls(), simulatorBefore, simulatorState(), breakerBefore, breakerState(), restartRequired);
    }

    private DemoHttpCall create(DemoSeedData seed, String correlationId, ScenarioTrace trace) {
        DemoHttpCall created = ordersHttp.create(seed.customerId(), seed.menuItemId(), 1, correlationId);
        trace.add(created);
        return created;
    }

    private int seedAndCreate(String correlationId, ScenarioTrace trace, Map<String, Object> parameters) {
        DemoSeedData seed = evidence.seedData();
        parameters.put("customerId", seed.customerId());
        parameters.put("menuItemId", seed.menuItemId());
        parameters.put("menuItem", seed.menuItemName());
        parameters.put("quantity", 1);
        return ordersHttp.orderIdFrom(create(seed, correlationId, trace));
    }

    private DemoOrderEvidence runPayment(int orderId, String purpose, boolean failure,
                                         String correlationId, ScenarioTrace trace) {
        trace.add(ordersHttp.pay(orderId, operationKey(purpose, correlationId), failure, correlationId));
        return evidence.order(orderId);
    }

    private String openBreaker(String id, Map<String, Object> parameters) {
        if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
            throw OrderException.conflict("El breaker ya está abierto. Ejecuta recuperación y vuelve a intentarlo.");
        }
        simulator.setAvailable(true);
        simulator.injectFailures(breakerMinimumCalls);
        parameters.put("injectedFailures", breakerMinimumCalls);
        long callsBefore = simulator.state().callCount();
        DemoSeedData seed = evidence.seedData();
        for (int attempt = 0; attempt < breakerMinimumCalls; attempt++) {
            paymentGateway.authorize(2_147_000_000 + attempt, seed.unitPrice(), operationKey("BREAKER-OPEN", id + attempt));
        }
        String openedState = circuitBreaker.getState().name();
        long callsAtOpen = simulator.state().callCount();
        paymentGateway.authorize(2_147_000_100, seed.unitPrice(), operationKey("BREAKER-BLOCKED", id));
        long callsAfterBlocked = simulator.state().callCount();
        parameters.put("simulatorCallsBefore", callsBefore);
        parameters.put("simulatorCallsAfterFailures", callsAtOpen);
        parameters.put("simulatorCallsAfterBlockedRequest", callsAfterBlocked);
        parameters.put("blockedCalls", circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());
        if (!"OPEN".equals(openedState) || callsAfterBlocked != callsAtOpen) {
            throw new IllegalStateException("El Circuit Breaker no abrió o la solicitud OPEN alcanzó al simulador.");
        }
        return "El breaker recorrió CLOSED → OPEN con fallos del simulador; una llamada bloqueada elevó el contador local sin aumentar callCount externo.";
    }

    private String recoverBreaker(String id, Map<String, Object> parameters, boolean successExpected) {
        if (!successExpected && circuitBreaker.getState() == CircuitBreaker.State.CLOSED) {
            openBreaker(id, parameters);
            if (circuitBreaker.getState() != CircuitBreaker.State.OPEN) {
                throw new IllegalStateException("No se pudo preparar el breaker para el caso de reapertura.");
            }
        }
        if (circuitBreaker.getState() != CircuitBreaker.State.OPEN) {
            throw OrderException.conflict("El breaker no está OPEN. Ejecuta primero el escenario de apertura.");
        }
        simulator.setAvailable(true);
        if (!successExpected) simulator.injectFailures(1);
        awaitHalfOpen();
        String halfOpenState = circuitBreaker.getState().name();
        if (!"HALF_OPEN".equals(halfOpenState)) throw new IllegalStateException("El breaker no entró a HALF_OPEN después de su espera configurada.");
        DemoSeedData seed = evidence.seedData();
        PaymentAuthorization result = paymentGateway.authorize(2_147_000_200, seed.unitPrice(),
                operationKey(successExpected ? "BREAKER-RECOVERY" : "BREAKER-REOPEN", id));
        String finalState = circuitBreaker.getState().name();
        parameters.put("halfOpenObserved", halfOpenState);
        parameters.put("finalState", finalState);
        parameters.put("probeAuthorized", result.authorized());
        String expected = successExpected ? "CLOSED" : "OPEN";
        if (!expected.equals(finalState) || result.authorized() != successExpected) {
            throw new IllegalStateException("La prueba real HALF_OPEN no produjo el estado esperado " + expected + ".");
        }
        return successExpected
                ? "El breaker pasó de OPEN a HALF_OPEN y cerró tras una autorización HTTP exitosa."
                : "El breaker pasó de OPEN a HALF_OPEN y volvió a OPEN al fallar la solicitud de prueba.";
    }

    private void awaitHalfOpen() {
        long waitMillis = Math.min(breakerOpenWaitMillis + 3000, 30_000);
        long deadline = System.nanoTime() + Duration.ofMillis(waitMillis).toNanos();
        while (circuitBreaker.getState() == CircuitBreaker.State.OPEN && System.nanoTime() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Se interrumpió la espera del Circuit Breaker.", exception);
            }
        }
    }

    private DemoSimulatorState simulatorState() {
        try {
            return simulator.state();
        } catch (RuntimeException exception) {
            throw OrderException.conflict("El simulador debe estar ejecutándose localmente con PAYMENT_SIMULATOR_DEMO=true.");
        }
    }

    private DemoBreakerState breakerState() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        float failureRate = metrics.getFailureRate();
        return new DemoBreakerState(circuitBreaker.getState().name(), metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfSuccessfulCalls(), metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfNotPermittedCalls(), Float.isFinite(failureRate) ? (double) failureRate : null);
    }

    private static String operationKey(String scenario, String suffix) {
        return "LAB04-" + scenario + "-" + suffix.replace("-", "").substring(0, Math.min(12,
                suffix.replace("-", "").length()));
    }

    private static final class ScenarioTrace {
        private final List<DemoHttpCall> calls = new ArrayList<>();
        void add(DemoHttpCall call) { calls.add(call); }
        void addAll(List<DemoHttpCall> values) { calls.addAll(values); }
        List<DemoHttpCall> calls() { return List.copyOf(calls); }
    }
}
