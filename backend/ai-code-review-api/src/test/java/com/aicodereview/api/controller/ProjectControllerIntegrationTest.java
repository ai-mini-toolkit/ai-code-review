package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.auth.LoginRequest;
import com.aicodereview.common.dto.auth.LoginResult;
import com.aicodereview.common.dto.project.CreateProjectRequest;
import com.aicodereview.common.dto.project.UpdateProjectRequest;
import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;
import com.aicodereview.common.dto.threshold.ThresholdRuleDTO;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.UserRepository;
import com.aicodereview.repository.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static Long createdProjectId;
    private static String adminToken;
    private static boolean authSetupDone = false;

    @BeforeAll
    static void cleanDatabase(@Autowired ProjectRepository repository) {
        repository.deleteAll();
    }

    @BeforeEach
    void setupAuth() {
        // Configure Apache HttpClient to avoid HttpRetryException on 401 responses
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        // Create admin user if not exists
        if (!authSetupDone) {
            // Delete and recreate to ensure correct password hash (Flyway may have created with different hash)
            userRepository.findByUsername("admin").ifPresent(userRepository::delete);
            User admin = User.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .email("admin@test.com")
                    .realName("Test Admin")
                    .role("ADMIN")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            // Get admin token
            LoginRequest loginReq = new LoginRequest("admin", "admin123");
            ResponseEntity<ApiResponse<LoginResult>> loginResp = restTemplate.exchange(
                    "/api/v1/auth/login",
                    HttpMethod.POST,
                    new HttpEntity<>(loginReq, jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );
            adminToken = loginResp.getBody().getData().getAccessToken();
            authSetupDone = true;
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private HttpHeaders adminJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

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
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

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

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects/" + createdProjectId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

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

        HttpEntity<UpdateProjectRequest> entity = new HttpEntity<>(request, adminJsonHeaders());

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
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        // Verify deletion - should return 404
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/v1/projects/" + createdProjectId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(6)
    void shouldReturn409ForDuplicateName() {
        CreateProjectRequest request = buildCreateRequest("duplicate-test-project");

        // Create first project
        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to create duplicate
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).isNotNull();
        assertThat(duplicate.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(duplicate.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_409");

        // Cleanup
        Map<String, Object> data = getData(first.getBody());
        Long id = ((Number) data.get("id")).longValue();
        restTemplate.exchange("/api/v1/projects/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(7)
    void shouldReturn404ForNonExistentProject() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects/999999", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

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

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> data = getData(createResponse.getBody());
        Long id = ((Number) data.get("id")).longValue();

        // Verify webhook_secret is not in response, only webhookSecretConfigured
        assertThat(data.containsKey("webhookSecret")).isFalse();
        assertThat(data.get("webhookSecretConfigured")).isEqualTo(true);

        // Also check GET response
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/v1/projects/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        Map<String, Object> getData = getData(getResponse.getBody());
        assertThat(getData.containsKey("webhookSecret")).isFalse();
        assertThat(getData.get("webhookSecretConfigured")).isEqualTo(true);

        // Cleanup
        restTemplate.exchange("/api/v1/projects/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(9)
    void shouldCacheProjectInRedisOnGet() {
        CreateProjectRequest request = buildCreateRequest("cache-test-project");

        // Create a project
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = getData(createResponse.getBody());
        Long id = ((Number) data.get("id")).longValue();

        // Clear cache to ensure clean state
        cacheManager.getCache("projects").clear();

        // First GET - should hit database and populate cache
        ResponseEntity<Map> firstGet = restTemplate.exchange(
                "/api/v1/projects/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(firstGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify cache entry exists after GET
        Object cachedValue = cacheManager.getCache("projects").get(id);
        assertThat(cachedValue).isNotNull();

        // Second GET - should hit cache (we verify cache has the entry)
        ResponseEntity<Map> secondGet = restTemplate.exchange(
                "/api/v1/projects/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(secondGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getData(secondGet.getBody()).get("name")).isEqualTo("cache-test-project");

        // Cleanup
        restTemplate.exchange("/api/v1/projects/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);

        // Verify cache is evicted after delete
        Object afterDelete = cacheManager.getCache("projects").get(id);
        assertThat(afterDelete).isNull();
    }

    @Test
    @Order(10)
    void shouldReturn422ForMissingRequiredFields() {
        CreateProjectRequest request = CreateProjectRequest.builder().build();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

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

    // ---- Threshold API Integration Tests (Story 6.1) ----

    @Test
    @Order(11)
    @SuppressWarnings("unchecked")
    void shouldReturnDefaultThresholdsForNewProject() {
        // Create a project
        CreateProjectRequest request = buildCreateRequest("threshold-test-project");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> projectData = getData(createResponse.getBody());
        Long projectId = ((Number) projectData.get("id")).longValue();

        // GET thresholds
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/thresholds", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> thresholds = getData(response.getBody());
        assertThat(thresholds.get("enabled")).isEqualTo(false);
        assertThat(thresholds.get("action")).isEqualTo("BLOCK_MERGE");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) thresholds.get("rules");
        assertThat(rules).hasSize(3);
        assertThat(rules.get(0).get("severity")).isEqualTo("CRITICAL");
        assertThat(rules.get(0).get("maxCount")).isEqualTo(0);

        // Cleanup
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(12)
    @SuppressWarnings("unchecked")
    void shouldUpdateThresholdsSuccessfully() {
        // Create a project
        CreateProjectRequest request = buildCreateRequest("threshold-update-project");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> projectData = getData(createResponse.getBody());
        Long projectId = ((Number) projectData.get("id")).longValue();

        // PUT thresholds
        ThresholdConfigDTO newConfig = ThresholdConfigDTO.builder()
                .enabled(true)
                .rules(List.of(
                        ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                        ThresholdRuleDTO.builder().severity("HIGH").maxCount(3).build(),
                        ThresholdRuleDTO.builder().totalIssues(15).build()
                ))
                .action("WARN_ONLY")
                .build();

        HttpEntity<ThresholdConfigDTO> entity = new HttpEntity<>(newConfig, adminJsonHeaders());

        ResponseEntity<Map> putResponse = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/thresholds",
                HttpMethod.PUT, entity, Map.class);

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putResponse.getBody().get("success")).isEqualTo(true);

        Map<String, Object> updated = getData(putResponse.getBody());
        assertThat(updated.get("enabled")).isEqualTo(true);
        assertThat(updated.get("action")).isEqualTo("WARN_ONLY");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) updated.get("rules");
        assertThat(rules).hasSize(3);

        // Verify persistence via GET
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/thresholds", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        Map<String, Object> persisted = getData(getResponse.getBody());
        assertThat(persisted.get("enabled")).isEqualTo(true);
        assertThat(persisted.get("action")).isEqualTo("WARN_ONLY");

        // Cleanup
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(13)
    void shouldReturn404ForThresholdsOfNonExistentProject() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects/999999/thresholds", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(response.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_404");
    }

    @Test
    @Order(14)
    void shouldReturn400ForInvalidThresholdSeverity() {
        // Create a project
        CreateProjectRequest request = buildCreateRequest("threshold-invalid-project");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> projectData = getData(createResponse.getBody());
        Long projectId = ((Number) projectData.get("id")).longValue();

        // PUT invalid config - invalid severity passes Jakarta validation but fails ThresholdMapper.validate() → 400
        ThresholdConfigDTO invalidConfig = ThresholdConfigDTO.builder()
                .enabled(true)
                .rules(List.of(
                        ThresholdRuleDTO.builder().severity("UNKNOWN_SEVERITY").maxCount(5).build()
                ))
                .action("BLOCK_MERGE")
                .build();

        HttpEntity<ThresholdConfigDTO> entity = new HttpEntity<>(invalidConfig, adminJsonHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/thresholds",
                HttpMethod.PUT, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(response.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_400");

        // Cleanup
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(15)
    @SuppressWarnings("unchecked")
    void shouldIncludeThresholdsInProjectDTOResponse() {
        // Create a project
        CreateProjectRequest request = buildCreateRequest("threshold-dto-project");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> projectData = getData(createResponse.getBody());
        Long projectId = ((Number) projectData.get("id")).longValue();

        // GET project - verify thresholds field is present in ProjectDTO
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/v1/projects/" + projectId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> data = getData(getResponse.getBody());
        assertThat(data).containsKey("thresholds");
        Map<String, Object> thresholds = (Map<String, Object>) data.get("thresholds");
        assertThat(thresholds).isNotNull();
        assertThat(thresholds.get("enabled")).isEqualTo(false);
        assertThat(thresholds.get("action")).isEqualTo("BLOCK_MERGE");

        // Cleanup
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(16)
    void shouldCacheThresholdsAndEvictOnUpdate() {
        // Create a project
        CreateProjectRequest request = buildCreateRequest("threshold-cache-project");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> projectData = getData(createResponse.getBody());
        Long projectId = ((Number) projectData.get("id")).longValue();

        // Clear threshold cache
        cacheManager.getCache("thresholds").clear();

        // First GET - populates cache
        ResponseEntity<Map> firstGet = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/thresholds", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(firstGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify cache entry exists
        Object cachedValue = cacheManager.getCache("thresholds").get(projectId);
        assertThat(cachedValue).isNotNull();

        // PUT update - should evict cache
        ThresholdConfigDTO newConfig = ThresholdConfigDTO.builder()
                .enabled(true)
                .rules(List.of(ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(1).build()))
                .action("WARN_ONLY")
                .build();

        HttpEntity<ThresholdConfigDTO> entity = new HttpEntity<>(newConfig, adminJsonHeaders());

        restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/thresholds",
                HttpMethod.PUT, entity, Map.class);

        // Verify cache was evicted after update
        Object afterUpdate = cacheManager.getCache("thresholds").get(projectId);
        assertThat(afterUpdate).isNull();

        // Cleanup
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }
}
