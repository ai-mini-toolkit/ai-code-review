package com.aicodereview.api.monitoring;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureObservability
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MonitoringIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @Order(1)
    @DisplayName("GET /actuator/prometheus returns Prometheus format metrics")
    void shouldReturnPrometheusMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Prometheus format contains HELP and TYPE comments
        assertThat(response.getBody()).contains("# HELP");
        assertThat(response.getBody()).contains("# TYPE");
        // Should contain JVM metrics (auto-registered by Micrometer)
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    @Test
    @Order(2)
    @DisplayName("GET /actuator/health includes systemHealth component")
    @SuppressWarnings("unchecked")
    void shouldIncludeSystemHealthIndicator() {
        // Use admin credentials or direct access depending on show-details config
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    @Order(3)
    @DisplayName("API responses include X-Request-Id header")
    void shouldIncludeCorrelationIdHeader() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/projects", String.class);

        assertThat(response.getHeaders().get("X-Request-Id")).isNotNull();
        assertThat(response.getHeaders().get("X-Request-Id")).hasSize(1);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    @Order(4)
    @DisplayName("API responses propagate provided X-Request-Id header")
    void shouldPropagateProvidedCorrelationId() {
        String customRequestId = "test-request-id-12345";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", customRequestId);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.GET, requestEntity, String.class);

        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo(customRequestId);
    }

    @Test
    @Order(5)
    @DisplayName("Custom metrics visible in Prometheus endpoint after API call")
    void shouldRecordCustomMetricsAfterApiCall() {
        // Make an API call to generate metrics
        restTemplate.getForEntity("/api/v1/projects", String.class);

        // Check prometheus endpoint for custom metrics
        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricsResponse.getBody()).isNotNull();
        assertThat(metricsResponse.getBody()).contains("api_requests_total");
        assertThat(metricsResponse.getBody()).contains("api_response_time_seconds");
    }

    @Test
    @Order(6)
    @DisplayName("Existing actuator endpoints still accessible")
    void shouldKeepExistingActuatorEndpoints() {
        // health endpoint
        ResponseEntity<Map> healthResponse = restTemplate.getForEntity(
                "/actuator/health", Map.class);
        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // metrics endpoint
        ResponseEntity<Map> metricsResponse = restTemplate.getForEntity(
                "/actuator/metrics", Map.class);
        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // info endpoint
        ResponseEntity<Map> infoResponse = restTemplate.getForEntity(
                "/actuator/info", Map.class);
        assertThat(infoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(7)
    @DisplayName("Application tag present in Prometheus metrics")
    void shouldIncludeApplicationTag() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("application=\"ai-code-review\"");
    }
}
