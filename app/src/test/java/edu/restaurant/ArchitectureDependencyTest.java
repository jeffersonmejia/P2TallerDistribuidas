package edu.restaurant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureDependencyTest {
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([^;]+);", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([^;]+);", Pattern.MULTILINE);

    @Test
    void domainAndApplicationPackagesDoNotDependOnInfrastructureOrPresentation() throws IOException {
        Path root = Path.of("src/main/java/edu/restaurant/features");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(root)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source);
                String packageName = match(PACKAGE, content);
                List<String> imports = IMPORT.matcher(content).results().map(result -> result.group(1)).toList();
                if (packageName.endsWith(".domain")) {
                    imports.stream().filter(value -> value.startsWith("org.springframework") || value.startsWith("jakarta.persistence") || value.startsWith("jakarta.servlet") || value.startsWith("java.net.http"))
                            .forEach(value -> violations.add(source + " imports framework/transport " + value));
                }
                if (packageName.endsWith(".application")) {
                    imports.stream().filter(value -> value.contains(".infrastructure.") || value.contains(".presentation."))
                            .forEach(value -> violations.add(source + " imports outward layer " + value));
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    private static String match(Pattern pattern, String content) {
        var matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }
}
