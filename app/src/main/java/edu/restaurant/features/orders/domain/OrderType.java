package edu.restaurant.features.orders.domain;

public enum OrderType {
    ONLINE("online"),
    IN_HOUSE("in-house");

    private final String databaseValue;

    OrderType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static OrderType fromDatabase(String value) {
        for (OrderType type : values()) {
            if (type.databaseValue.equals(value)) return type;
        }
        throw new IllegalArgumentException("Tipo de orden desconocido");
    }
}
