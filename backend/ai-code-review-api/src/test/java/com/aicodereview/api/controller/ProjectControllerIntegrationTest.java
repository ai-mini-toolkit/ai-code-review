package com.aicodereview.api.controller;

import com.aicodereview.common.dto.project.CreateProjectRequest;
import com.aicodereview.common.dto.project.UpdateProjectRequest;
import com.aicodereview.repository.ProjectRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CacheManager cacheManager;

    private static Long createdProjectId;

    @BeforeAll
    static void cleanDatabase(@Autowired ProjectRepository repository) {
        repository.deleteAll();
    }

    private CreateProjectRequest buildCreateRequest(String name) {
        return CreateProjectRequest.builder()
                .name(name)
                .description("Test project description")
                .enabled(true)
                .gitPlatform("GitHub")
                .repoUrl("https://github.com/test/" + name)
                .webhookSecret("test-secret-123")
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(Map<String, Object> body) {
        return (Map<String, Object>) body.get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getError(Map<String, Object> body) {
        return (Map<String, Object>) body.get("error");
    }

    @Test
    @Order(1)
    void shouldCreateProject() {
        CreateProjectRequest request = buildCreateRequest("integration-test-project");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/projects", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("name")).isEqualTo("integration-test-project");
        assertThat(data.get("enabled")).isEqualTo(true);
        assertThat(data.get("gitPlatform")).isEqualTo("GitHub");
        assertThat(data.get("repoUrl")).isEqualTo("https://github.com/test/integration-test-project");
        assertThat(data.get("id")).isNotNull();

        createdProjectId = ((Number) data.get("id")).longValue();
    }

    @Test
    @Order(2)
    void shouldListProjects() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/projects", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isNotEmpty();
    }

    @Test
    @Order(3)
    void shouldGetProjectById() {
        assertThat(createdProjectId).isNotNull();

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/projects/" + createdProjectId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("name")).isEqualTo("integration-test-project");
        assertThat(((Number) data.get("id")).longValue()).isEqualTo(createdProjectId);
    }

    @Test
    @Order(4)
    void shouldUpdateProject() {
        assertThat(createdProjectId).isNotNull();

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .description("Updated description")
                .enabled(false)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateProjectRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects/" + createdProjectId,
                HttpMethod.PUT, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("description")).isEqualTo("Updated description");
        assertThat(data.get("enabled")).isEqualTo(false);
        assertThat(data.get("name")).isEqualTo("integration-test-project");
    }

    @Test
    @Order(5)
    void shouldDeleteProject() {
        assertThat(createdProjectId).isNotNull();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects/" + createdProjectId,
                HttpMethod.DELETE, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        // Verify deletion - should return 404
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
                "/api/v1/projects/" + createdProjectId, Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(6)
    void shouldReturn409ForDuplicateName() {
        CreateProjectRequest request = buildCreateRequest("duplicate-test-project");

        // Create first project
        ResponseEntity<Map> first = restTemplate.postForEntity(
                "/api/v1/projects", request, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to create duplicate
        ResponseEntity<Map> duplicate = restTemplate.postForEntity(
                "/api/v1/projects", request, Map.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).isNotNull();
        assertThat(duplicate.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(duplicate.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_409");

        // Cleanup
        Map<String, Object> data = getData(first.getBody());
        Long id = ((Number) data.get("id")).longValue();
        restTemplate.delete("/api/v1/projects/" + id);
    }

    @Test
    @Order(7)
    void shouldReturn404ForNonExistentProject() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/projects/999999", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(response.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_404");
    }

    @Test
    @Order(8)
    void shouldNotExposeWebhookSecretInResponse() {
        CreateProjectRequest request = buildCreateRequest("secret-test-project");

        ResponseEntity<Map> createResponse = restTemplate.postForEntity(
                "/api/v1/projects", request, Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> data = getData(createResponse.getBody());
        Long id = ((Number) data.get("id")).longValue();

        // Verify webhook_secret is not in response, only webhookSecretConfigured
        assertThat(data.containsKey("webhookSecret")).isFalse();
        assertThat(data.get("webhookSecretConfigured")).isEqualTo(true);

        // Also check GET response
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
                "/api/v1/projects/" + id, Map.class);
        Map<String, Object> getData = getData(getResponse.getBody());
        assertThat(getData.containsKey("webhookSecret")).isFalse();
        assertThat(getData.get("webhookSecretConfigured")).isEqualTo(true);

        // Cleanup
        restTemplate.delete("/api/v1/projects/" + id);
    }

    @Test
    @Order(9)
    void shouldCacheProjectInRedisOnGet() {
        CreateProjectRequest request = buildCreateRequest("cache-test-project");

        // Create a project
        ResponseEntity<Map> createResponse = restTemplate.postForEntity(
                "/api/v1/projects", request, Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = getData(createResponse.getBody());
        Long id = ((Number) data.get("id")).longValue();

        // Clear cache to ensure clean state
        cacheManager.getCache("projects").clear();

        // First GET - should hit database and populate cache
        ResponseEntity<Map> firstGet = restTemplate.getForEntity(
                "/api/v1/projects/" + id, Map.class);
        assertThat(firstGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify cache entry exists after GET
        Object cachedValue = cacheManager.getCache("projects").get(id);
        assertThat(cachedValue).isNotNull();

        // Second GET - should hit cache (we verify cache has the entry)
        ResponseEntity<Map> secondGet = restTemplate.getForEntity(
                "/api/v1/projects/" + id, Map.class);
        assertThat(secondGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getData(secondGet.getBody()).get("name")).isEqualTo("cache-test-project");

        // Cleanup
        restTemplate.delete("/api/v1/projects/" + id);

        // Verify cache is evicted after delete
        Object afterDelete = cacheManager.getCache("projects").get(id);
        assertThat(afterDelete).isNull();
    }

    @Test
    @Order(10)
    void shouldReturn422ForMissingRequiredFields() {
        CreateProjectRequest request = CreateProjectRequest.builder().build();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/projects", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(response.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_422");
        assertThat(error.get("message")).isEqualTo("Validation failed");

        // Verify field-level errors are present
        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) error.get("details");
        assertThat(details).isNotNull();
        assertThat(details).containsKey("name");
        assertThat(details).containsKey("gitPlatform");
        assertThat(details).containsKey("repoUrl");
        assertThat(details).containsKey("webhookSecret");
    }
}
