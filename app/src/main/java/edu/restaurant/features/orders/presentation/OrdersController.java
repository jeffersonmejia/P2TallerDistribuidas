package edu.restaurant.features.orders.presentation;

import edu.restaurant.features.orders.application.OrderApplicationService;
import edu.restaurant.features.orders.application.OrderDetail;
import edu.restaurant.features.orders.application.OrderLineInput;
import edu.restaurant.features.orders.application.OrderLineView;
import edu.restaurant.features.orders.application.OrderPage;
import edu.restaurant.features.orders.application.OrderPaymentResult;
import edu.restaurant.features.orders.application.PaymentOperation;
import edu.restaurant.features.orders.application.OrderSummary;
import edu.restaurant.features.orders.domain.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
public class OrdersController {
    private final OrderApplicationService orders;
    private final Environment environment;
    private final PaymentOperation paymentOperation;

    public OrdersController(OrderApplicationService orders, Environment environment, PaymentOperation paymentOperation) {
        this.orders = orders;
        this.environment = environment;
        this.paymentOperation = paymentOperation;
    }

    @GetMapping
    public OrderPageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") String search) {
        return toResponse(orders.list(search, page, size));
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse get(@PathVariable @Min(1) int orderId) {
        return toResponse(orders.get(orderId));
    }

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderType type = parseOrderType(request.orderType());
        List<OrderLineInput> lines = request.items() == null ? List.of() : request.items().stream()
                .map(item -> new OrderLineInput(item.itemId(), item.quantity())).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(orders.create(request.customerId(), request.tableId(), type, lines)));
    }

    @PutMapping("/{orderId}/items")
    public OrderDetailResponse replaceItems(@PathVariable @Min(1) int orderId, @Valid @RequestBody ReplaceOrderItemsRequest request) {
        List<OrderLineInput> lines = request.items().stream().map(item -> new OrderLineInput(item.itemId(), item.quantity())).toList();
        return toResponse(orders.replaceItems(orderId, lines));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancel(@PathVariable @Min(1) int orderId) {
        orders.cancel(orderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/payments")
    public ResponseEntity<PaymentConfirmationResponse> confirmPayment(
            @PathVariable @Min(1) int orderId,
            @Valid @RequestBody ConfirmOrderPaymentRequest request,
            @RequestParam(defaultValue = "false") boolean simulateFailure) {
        boolean enabled = java.util.Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> profile.equals("demo") || profile.equals("test"));
        if (simulateFailure && !enabled) {
            throw edu.restaurant.features.orders.domain.OrderException.validation("La inyección de fallos solo está disponible en el perfil demo o test.");
        }
        OrderPaymentResult result = paymentOperation.confirmPayment(orderId, request.paymentMethod(), request.idempotencyKey(), simulateFailure);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PaymentConfirmationResponse(
                result.paymentId(), result.orderId(), result.amount(), result.status()));
    }

    private OrderType parseOrderType(String value) {
        try {
            return OrderType.fromDatabase(value == null ? "" : value.trim().toLowerCase());
        } catch (IllegalArgumentException exception) {
            throw edu.restaurant.features.orders.domain.OrderException.validation("El tipo de orden debe ser online o in-house.");
        }
    }

    private OrderPageResponse toResponse(OrderPage page) {
        return new OrderPageResponse(page.content().stream().map(this::toResponse).toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    private OrderSummaryResponse toResponse(OrderSummary summary) {
        return new OrderSummaryResponse(summary.id(), summary.orderDate(), summary.customerName(), summary.orderType(),
                summary.tableId(), summary.status(), summary.total(), summary.paid());
    }

    private OrderDetailResponse toResponse(OrderDetail detail) {
        List<OrderLineResponse> items = detail.items().stream().map(this::toResponse).toList();
        return new OrderDetailResponse(detail.id(), detail.orderDate(), detail.customerId(), detail.customerName(), detail.tableId(),
                detail.orderType(), detail.status(), items, detail.total(), detail.paidAmount(), detail.paymentMethod());
    }

    private OrderLineResponse toResponse(OrderLineView line) {
        return new OrderLineResponse(line.itemId(), line.name(), line.quantity(), line.unitPrice(), line.lineTotal());
    }
}
