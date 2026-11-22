package edu.restaurant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class TestDatabase {
    private static final Map<String, String> environment = loadEnvironment();
    private static final PostgreSQLContainer postgres = startDatabaseIfNeeded();

    private TestDatabase() {}

    static void configure(DynamicPropertyRegistry registry) {
        if (postgres != null) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            registry.add("spring.datasource.url", TestDatabase::databaseUrl);
            registry.add("spring.datasource.username", () -> value("DB_USER", "restaurant_app"));
            registry.add("spring.datasource.password", () -> value("DB_PASSWORD", ""));
        }
    }

    private static PostgreSQLContainer startDatabaseIfNeeded() {
        if (localDatabaseAcceptsConnection()) return null;
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:16-alpine");
        container.start();
        Runtime.getRuntime().addShutdownHook(new Thread(container::stop, "stop-test-postgres"));
        return container;
    }

    private static boolean localDatabaseAcceptsConnection() {
        try (var ignored = DriverManager.getConnection(databaseUrl(), value("DB_USER", "restaurant_app"), value("DB_PASSWORD", ""))) {
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static String databaseUrl() {
        return "jdbc:postgresql://" + value("DB_HOST", "localhost") + ":" + value("DB_PORT", "5432") + "/" + value("DB_NAME", "restaurant_db");
    }

    private static String value(String key, String fallback) {
        return environment.getOrDefault(key, fallback);
    }

    private static Map<String, String> loadEnvironment() {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path envFile = directory.resolve(".env");
            if (Files.isRegularFile(envFile)) {
                try {
                    for (String line : Files.readAllLines(envFile)) {
                        String[] pair = line.split("=", 2);
                        if (pair.length == 2 && !pair[0].isBlank() && !pair[0].startsWith("#")) {
                            values.putIfAbsent(pair[0].trim(), pair[1].trim());
                        }
                    }
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("No se pudo leer la configuración local de pruebas", exception);
                }
                break;
            }
            directory = directory.getParent();
        }
        return values;
    }
}
