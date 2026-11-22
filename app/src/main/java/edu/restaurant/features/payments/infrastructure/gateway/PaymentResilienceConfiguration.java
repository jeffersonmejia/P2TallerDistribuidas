package edu.restaurant.features.payments.infrastructure.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentResilienceConfiguration {
    @Bean
    public CircuitBreaker paymentSimulatorCircuitBreaker(
            @Value("${payment.circuit-breaker.sliding-window-size}") int windowSize,
            @Value("${payment.circuit-breaker.minimum-calls}") int minimumCalls,
            @Value("${payment.circuit-breaker.failure-threshold}") float failureThreshold,
            @Value("${payment.circuit-breaker.open-wait-ms}") long openWaitMillis,
            @Value("${payment.circuit-breaker.half-open-calls}") int halfOpenCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureThreshold)
                .waitDurationInOpenState(Duration.ofMillis(openWaitMillis))
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreaker.of("paymentSimulator", config);
    }
}
