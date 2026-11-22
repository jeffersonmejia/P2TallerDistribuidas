package edu.restaurant.features.demonstrations.application;

public record DemoHttpCall(String method, String path, int status, String correlationId, String body) {
}
