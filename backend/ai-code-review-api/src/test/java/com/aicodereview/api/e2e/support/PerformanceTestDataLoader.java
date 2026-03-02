package com.aicodereview.api.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads performance test datasets from classpath resources.
 *
 * <p>Test data is organized by size level (small, medium, large, xlarge) under
 * {@code performance-test-data/} with a unified diff file and an expected-issues
 * JSON file per level.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * PerformanceTestDataLoader loader = new PerformanceTestDataLoader();
 * PerformanceTestDataLoader.Dataset dataset = loader.loadDataset("small");
 * String diff = dataset.diff();
 * long maxTimeMs = dataset.maxReviewTimeMs();
 * </pre>
 *
 * @since 10.1.0
 */
public class PerformanceTestDataLoader {

    private static final String BASE_PATH = "performance-test-data";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * A loaded performance test dataset containing the diff content and expected results.
     *
     * @param level           dataset level (small, medium, large, xlarge)
     * @param diff            unified diff content
     * @param expectedIssues  parsed expected-issues.json as JsonNode
     * @param minIssues       minimum expected issue count
     * @param maxIssues       maximum expected issue count
     * @param maxReviewTimeMs performance target in milliseconds
     */
    public record Dataset(
            String level,
            String diff,
            JsonNode expectedIssues,
            int minIssues,
            int maxIssues,
            long maxReviewTimeMs
    ) {}

    /**
     * Loads a dataset by level name.
     *
     * @param level one of: "small", "medium", "large", "xlarge"
     * @return loaded dataset with diff and expected results
     * @throws IllegalArgumentException if the level is not found
     */
    public Dataset loadDataset(String level) {
        try {
            String diffPath = BASE_PATH + "/" + level + "/" + getDiffFilename(level);
            String expectedPath = BASE_PATH + "/" + level + "/expected-issues.json";

            String diff = loadResource(diffPath);
            String expectedJson = loadResource(expectedPath);
            JsonNode expected = MAPPER.readTree(expectedJson);

            int minIssues = expected.path("minIssues").asInt(0);
            int maxIssues = expected.path("maxIssues").asInt(100);
            long maxTimeMs = expected.path("performanceTarget").path("maxReviewTimeMs").asLong(60000);

            return new Dataset(level, diff, expected, minIssues, maxIssues, maxTimeMs);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load dataset for level: " + level, e);
        }
    }

    /**
     * Returns available dataset levels.
     */
    public String[] getAvailableLevels() {
        return new String[]{"small", "medium", "large", "xlarge"};
    }

    private String getDiffFilename(String level) {
        return switch (level) {
            case "small" -> "100-lines.diff";
            case "medium" -> "500-lines.diff";
            case "large" -> "1000-lines.diff";
            case "xlarge" -> "5000-lines.diff";
            default -> throw new IllegalArgumentException("Unknown level: " + level);
        };
    }

    private String loadResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
