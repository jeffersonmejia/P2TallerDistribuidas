package edu.restaurant.features.payments.presentation;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo")
@RequestMapping("/api/demo/payments")
public class PaymentDiagnosticsController {
    private final CircuitBreaker circuitBreaker;

    public PaymentDiagnosticsController(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @GetMapping("/circuit-breaker")
    public ResponseEntity<CircuitBreakerSnapshot> status(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        float failureRate = metrics.getFailureRate();
        return ResponseEntity.ok(new CircuitBreakerSnapshot(circuitBreaker.getState().name(), metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfSuccessfulCalls(), metrics.getNumberOfFailedCalls(), metrics.getNumberOfNotPermittedCalls(),
                Float.isFinite(failureRate) ? (double) failureRate : null));
    }

    private boolean isLoopback(String address) {
        try { return InetAddress.getByName(address).isLoopbackAddress(); }
        catch (Exception ignored) { return false; }
    }
}
