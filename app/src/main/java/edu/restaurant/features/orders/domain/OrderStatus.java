package edu.restaurant.features.orders.domain;

public enum OrderStatus {
    PENDING("pending"),
    IN_PROGRESS("in-progress"),
    ALMOST_READY("almost-ready"),
    COMPLETED("completed"),
    CANCELED("canceled");

    private final String databaseValue;

    OrderStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static OrderStatus fromDatabase(String value) {
        for (OrderStatus status : values()) {
            if (status.databaseValue.equals(value)) return status;
        }
        throw new IllegalArgumentException("Estado de orden desconocido");
    }
}
