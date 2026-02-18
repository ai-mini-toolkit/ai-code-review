package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.auth.LoginRequest;
import com.aicodereview.common.dto.auth.LoginResult;
import com.aicodereview.common.dto.prompttemplate.CreatePromptTemplateRequest;
import com.aicodereview.common.dto.prompttemplate.UpdatePromptTemplateRequest;
import com.aicodereview.repository.PromptTemplateRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PromptTemplateControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PromptTemplateRepository promptTemplateRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static Long createdTemplateId;
    private static String adminToken;
    private static boolean authSetupDone = false;

    private static final String VALID_TEMPLATE = "Review {{file_name}} for {{category}} issues:\n{{#each issues}}\n- Line {{line}}: {{description}}\n{{/each}}";

    @BeforeAll
    static void cleanDatabase(@Autowired PromptTemplateRepository repository) {
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

    private CreatePromptTemplateRequest buildCreateRequest(String name, String category) {
        return CreatePromptTemplateRequest.builder()
                .name(name)
                .category(category)
                .templateContent(VALID_TEMPLATE)
                .version(1)
                .enabled(true)
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
    void shouldCreatePromptTemplate() {
        CreatePromptTemplateRequest request = buildCreateRequest("security-review-v1", "security");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("name")).isEqualTo("security-review-v1");
        assertThat(data.get("category")).isEqualTo("security");
        assertThat(data.get("templateContent")).isEqualTo(VALID_TEMPLATE);
        assertThat(data.get("version")).isEqualTo(1);
        assertThat(data.get("enabled")).isEqualTo(true);
        assertThat(data.get("id")).isNotNull();

        createdTemplateId = ((Number) data.get("id")).longValue();
    }

    @Test
    @Order(2)
    void shouldListPromptTemplates() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.GET,
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
    void shouldFilterByCategory() {
        // Create a performance template
        CreatePromptTemplateRequest perfRequest = buildCreateRequest("performance-review-v1", "performance");
        restTemplate.exchange("/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(perfRequest, adminHeaders()), Map.class);

        // Filter by category=security
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates?category=security", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isNotEmpty();
        assertThat(data).allMatch(m -> "security".equals(m.get("category")));
    }

    @Test
    @Order(4)
    void shouldGetPromptTemplateById() {
        assertThat(createdTemplateId).isNotNull();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates/" + createdTemplateId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("name")).isEqualTo("security-review-v1");
        assertThat(((Number) data.get("id")).longValue()).isEqualTo(createdTemplateId);
    }

    @Test
    @Order(5)
    void shouldUpdatePromptTemplate() {
        assertThat(createdTemplateId).isNotNull();

        String updatedContent = "Updated: Review {{file_name}} for security.";
        UpdatePromptTemplateRequest request = UpdatePromptTemplateRequest.builder()
                .templateContent(updatedContent)
                .version(2)
                .enabled(false)
                .build();

        HttpEntity<UpdatePromptTemplateRequest> entity = new HttpEntity<>(request, adminJsonHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates/" + createdTemplateId,
                HttpMethod.PUT, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("templateContent")).isEqualTo(updatedContent);
        assertThat(data.get("version")).isEqualTo(2);
        assertThat(data.get("enabled")).isEqualTo(false);
        assertThat(data.get("name")).isEqualTo("security-review-v1");
    }

    @Test
    @Order(6)
    void shouldDeletePromptTemplate() {
        // Create a template to delete
        CreatePromptTemplateRequest request = buildCreateRequest("template-to-delete", "style");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long deleteId = ((Number) getData(createResponse.getBody()).get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates/" + deleteId,
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        // Verify deletion - should return 404
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/v1/prompt-templates/" + deleteId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(7)
    void shouldReturn409ForDuplicateName() {
        CreatePromptTemplateRequest request = buildCreateRequest("duplicate-template-test", "correctness");

        // Create first
        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to create duplicate
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).isNotNull();
        assertThat(duplicate.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(duplicate.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_409");

        // Cleanup
        Long id = ((Number) getData(first.getBody()).get("id")).longValue();
        restTemplate.exchange("/api/v1/prompt-templates/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(8)
    void shouldReturn404ForNonExistentTemplate() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates/999999", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(response.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_404");
    }

    @Test
    @Order(9)
    void shouldReturn422ForMissingRequiredFields() {
        CreatePromptTemplateRequest request = CreatePromptTemplateRequest.builder().build();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(response.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_422");
        assertThat(error.get("message")).isEqualTo("Validation failed");

        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) error.get("details");
        assertThat(details).isNotNull();
        assertThat(details).containsKey("name");
        assertThat(details).containsKey("category");
        assertThat(details).containsKey("templateContent");
    }

    @Test
    @Order(10)
    void shouldPreviewTemplate() {
        assertThat(createdTemplateId).isNotNull();

        // First update the template back to the valid template content for preview
        UpdatePromptTemplateRequest updateReq = UpdatePromptTemplateRequest.builder()
                .templateContent(VALID_TEMPLATE)
                .build();
        restTemplate.exchange("/api/v1/prompt-templates/" + createdTemplateId,
                HttpMethod.PUT, new HttpEntity<>(updateReq, adminJsonHeaders()), Map.class);

        Map<String, Object> sampleData = new HashMap<>();
        sampleData.put("file_name", "UserService.java");
        sampleData.put("category", "security");
        sampleData.put("issues", List.of(
                Map.of("line", 42, "description", "SQL injection vulnerability"),
                Map.of("line", 88, "description", "Hardcoded password")
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(sampleData, adminJsonHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates/" + createdTemplateId + "/preview",
                HttpMethod.POST, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("renderedContent")).isNotNull();
        String rendered = (String) data.get("renderedContent");
        assertThat(rendered).contains("UserService.java");
        assertThat(rendered).contains("security");
        assertThat(rendered).contains("SQL injection vulnerability");
        assertThat(rendered).contains("Hardcoded password");
        assertThat(data.get("renderTimeMs")).isNotNull();
    }

    @Test
    @Order(11)
    void shouldReturn422ForInvalidMustacheSyntax() {
        CreatePromptTemplateRequest request = CreatePromptTemplateRequest.builder()
                .name("invalid-syntax-template")
                .category("security")
                .templateContent("{{#each items}Missing closing tag")
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(response.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_422");
    }

    @Test
    @Order(12)
    void shouldFilterByEnabled() {
        // The security-review-v1 template was disabled in Order 5 update test
        // Create an enabled template to ensure we have both states
        CreatePromptTemplateRequest enabledReq = buildCreateRequest("enabled-filter-test", "best_practices");
        ResponseEntity<Map> createResp = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(enabledReq, adminHeaders()), Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Filter by enabled=true
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates?enabled=true", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isNotEmpty();
        assertThat(data).allMatch(m -> Boolean.TRUE.equals(m.get("enabled")));
    }

    @Test
    @Order(13)
    void shouldFilterByCategoryAndEnabled() {
        // Filter by category=security AND enabled=true
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/prompt-templates?category=best_practices&enabled=true", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isNotEmpty();
        assertThat(data).allMatch(m ->
                "best_practices".equals(m.get("category")) && Boolean.TRUE.equals(m.get("enabled")));
    }

    @Test
    @Order(14)
    void shouldCachePromptTemplateInRedisOnGet() {
        CreatePromptTemplateRequest request = buildCreateRequest("cache-test-template", "maintainability");

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/prompt-templates", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = getData(createResponse.getBody());
        Long id = ((Number) data.get("id")).longValue();

        // Clear cache to ensure clean state
        cacheManager.getCache("prompt-templates").clear();

        // First GET - should hit database and populate cache
        ResponseEntity<Map> firstGet = restTemplate.exchange(
                "/api/v1/prompt-templates/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(firstGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify cache entry exists after GET
        Object cachedValue = cacheManager.getCache("prompt-templates").get(id);
        assertThat(cachedValue).isNotNull();

        // Second GET - should hit cache
        ResponseEntity<Map> secondGet = restTemplate.exchange(
                "/api/v1/prompt-templates/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(secondGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getData(secondGet.getBody()).get("name")).isEqualTo("cache-test-template");

        // Cleanup
        restTemplate.exchange("/api/v1/prompt-templates/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);

        // Verify cache is evicted after delete
        Object afterDelete = cacheManager.getCache("prompt-templates").get(id);
        assertThat(afterDelete).isNull();
    }
}
