package edu.restaurant.features.demonstrations.application;

import java.util.List;

public interface DemoOrdersHttpPort {
    DemoHttpCall create(int customerId, int itemId, int quantity, String correlationId);

    DemoHttpCall get(int orderId, String correlationId);

    DemoHttpCall replaceItems(int orderId, int itemId, int quantity, String correlationId);

    DemoHttpCall cancel(int orderId, String correlationId);

    DemoHttpCall pay(int orderId, String operationKey, boolean simulateFailure, String correlationId);

    DemoHttpCall payWithInvalidQuantity(int customerId, int itemId, String correlationId);

    List<DemoHttpCall> payConcurrently(int orderId, String operationPrefix, String correlationPrefix);

    int orderIdFrom(DemoHttpCall call);
}
