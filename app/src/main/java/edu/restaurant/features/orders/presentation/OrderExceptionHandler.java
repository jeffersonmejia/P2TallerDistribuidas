package edu.restaurant.features.orders.presentation;

import edu.restaurant.features.orders.domain.OrderException;
import edu.restaurant.shared.infrastructure.CorrelationIdFilter;
import edu.restaurant.shared.presentation.ApiError;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrdersController.class)
public class OrderExceptionHandler {
    @ExceptionHandler(OrderException.class)
    public ResponseEntity<ApiError> handleOrderException(OrderException exception) {
        HttpStatus status = switch (exception.reason()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STATE_CONFLICT, PAYMENT_ALREADY_CONFIRMED -> HttpStatus.CONFLICT;
            case PAYMENT_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        String code = switch (exception.reason()) {
            case VALIDATION -> "VALIDATION_ERROR";
            case NOT_FOUND -> "NOT_FOUND";
            case STATE_CONFLICT -> "ORDER_STATE_CONFLICT";
            case PAYMENT_ALREADY_CONFIRMED -> "PAYMENT_ALREADY_CONFIRMED";
            case PAYMENT_UNAVAILABLE -> "PAYMENT_PROVIDER_UNAVAILABLE";
        };
        ApiError body = new ApiError(Instant.now(), status.value(), code, exception.getMessage(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(body);
    }
}
