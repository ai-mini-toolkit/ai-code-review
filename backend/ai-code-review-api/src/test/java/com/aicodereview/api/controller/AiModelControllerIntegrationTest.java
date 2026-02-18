package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.aimodel.CreateAiModelRequest;
import com.aicodereview.common.dto.aimodel.UpdateAiModelRequest;
import com.aicodereview.common.dto.auth.LoginRequest;
import com.aicodereview.common.dto.auth.LoginResult;
import com.aicodereview.repository.AiModelConfigRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiModelControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AiModelConfigRepository aiModelConfigRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static Long createdModelId;
    private static String adminToken;
    private static boolean authSetupDone = false;

    @BeforeAll
    static void cleanDatabase(@Autowired AiModelConfigRepository repository) {
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

    private CreateAiModelRequest buildCreateRequest(String name) {
        return CreateAiModelRequest.builder()
                .name(name)
                .provider("openai")
                .modelName("gpt-4")
                .apiKey("sk-test-key-123456")
                .apiEndpoint("https://api.openai.com/v1")
                .temperature(new BigDecimal("0.70"))
                .maxTokens(4000)
                .timeoutSeconds(30)
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
    void shouldCreateAiModelConfig() {
        CreateAiModelRequest request = buildCreateRequest("test-openai-model");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("name")).isEqualTo("test-openai-model");
        assertThat(data.get("provider")).isEqualTo("openai");
        assertThat(data.get("modelName")).isEqualTo("gpt-4");
        assertThat(data.get("apiEndpoint")).isEqualTo("https://api.openai.com/v1");
        assertThat(data.get("enabled")).isEqualTo(true);
        assertThat(data.get("id")).isNotNull();

        createdModelId = ((Number) data.get("id")).longValue();
    }

    @Test
    @Order(2)
    void shouldListAiModels() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models", HttpMethod.GET,
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
    void shouldFilterByProvider() {
        // Create an anthropic model
        CreateAiModelRequest anthropicRequest = CreateAiModelRequest.builder()
                .name("test-anthropic-model")
                .provider("anthropic")
                .modelName("claude-opus")
                .apiKey("sk-ant-test-key-789")
                .apiEndpoint("https://api.anthropic.com/v1")
                .build();
        restTemplate.exchange("/api/v1/ai-models", HttpMethod.POST,
                new HttpEntity<>(anthropicRequest, adminHeaders()), Map.class);

        // Filter by provider=openai
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models?provider=openai", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isNotEmpty();
        assertThat(data).allMatch(m -> "openai".equals(m.get("provider")));
    }

    @Test
    @Order(4)
    void shouldGetAiModelById() {
        assertThat(createdModelId).isNotNull();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models/" + createdModelId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("name")).isEqualTo("test-openai-model");
        assertThat(((Number) data.get("id")).longValue()).isEqualTo(createdModelId);
    }

    @Test
    @Order(5)
    void shouldUpdateAiModelConfig() {
        assertThat(createdModelId).isNotNull();

        UpdateAiModelRequest request = UpdateAiModelRequest.builder()
                .modelName("gpt-4-turbo")
                .temperature(new BigDecimal("0.50"))
                .maxTokens(8000)
                .enabled(false)
                .build();

        HttpEntity<UpdateAiModelRequest> entity = new HttpEntity<>(request, adminJsonHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models/" + createdModelId,
                HttpMethod.PUT, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("modelName")).isEqualTo("gpt-4-turbo");
        assertThat(data.get("enabled")).isEqualTo(false);
        assertThat(data.get("maxTokens")).isEqualTo(8000);
        assertThat(data.get("name")).isEqualTo("test-openai-model");
    }

    @Test
    @Order(6)
    void shouldDeleteAiModelConfig() {
        // Create a model to delete
        CreateAiModelRequest request = buildCreateRequest("model-to-delete");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/ai-models", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long deleteId = ((Number) getData(createResponse.getBody()).get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models/" + deleteId,
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        // Verify deletion - should return 404
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/v1/ai-models/" + deleteId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(7)
    void shouldReturn409ForDuplicateName() {
        CreateAiModelRequest request = buildCreateRequest("duplicate-model-test");

        // Create first
        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/v1/ai-models", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to create duplicate
        ResponseEntity<Map> duplicate = restTemplate.exchange(
                "/api/v1/ai-models", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).isNotNull();
        assertThat(duplicate.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(duplicate.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_409");

        // Cleanup
        Long id = ((Number) getData(first.getBody()).get("id")).longValue();
        restTemplate.exchange("/api/v1/ai-models/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(8)
    void shouldReturn404ForNonExistentModel() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models/999999", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);

        Map<String, Object> error = getError(response.getBody());
        assertThat(error.get("code")).isEqualTo("ERR_404");
    }

    @Test
    @Order(9)
    void shouldNotExposeApiKeyInResponse() {
        CreateAiModelRequest request = buildCreateRequest("api-key-test-model");

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/ai-models", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> data = getData(createResponse.getBody());
        Long id = ((Number) data.get("id")).longValue();

        // Verify api_key is not in response, only apiKeyConfigured
        assertThat(data.containsKey("apiKey")).isFalse();
        assertThat(data.get("apiKeyConfigured")).isEqualTo(true);

        // Also check GET response
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/v1/ai-models/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        Map<String, Object> getData = getData(getResponse.getBody());
        assertThat(getData.containsKey("apiKey")).isFalse();
        assertThat(getData.get("apiKeyConfigured")).isEqualTo(true);

        // Cleanup
        restTemplate.exchange("/api/v1/ai-models/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(10)
    void shouldReturn422ForMissingRequiredFields() {
        CreateAiModelRequest request = CreateAiModelRequest.builder().build();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models", HttpMethod.POST,
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
        assertThat(details).containsKey("provider");
        assertThat(details).containsKey("modelName");
        assertThat(details).containsKey("apiKey");
    }

    @Test
    @Order(11)
    void shouldTestConnection() {
        assertThat(createdModelId).isNotNull();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/ai-models/" + createdModelId + "/test", HttpMethod.POST,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        // The test connection result has its own success field
        assertThat(data.containsKey("success")).isTrue();
        assertThat(data.containsKey("message")).isTrue();
    }

    @Test
    @Order(12)
    void shouldCacheAiModelInRedisOnGet() {
        CreateAiModelRequest request = buildCreateRequest("cache-test-ai-model");

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/ai-models", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = getData(createResponse.getBody());
        Long id = ((Number) data.get("id")).longValue();

        // Clear cache to ensure clean state
        cacheManager.getCache("ai-models").clear();

        // First GET - should hit database and populate cache
        ResponseEntity<Map> firstGet = restTemplate.exchange(
                "/api/v1/ai-models/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(firstGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify cache entry exists after GET
        Object cachedValue = cacheManager.getCache("ai-models").get(id);
        assertThat(cachedValue).isNotNull();

        // Second GET - should hit cache
        ResponseEntity<Map> secondGet = restTemplate.exchange(
                "/api/v1/ai-models/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(secondGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getData(secondGet.getBody()).get("name")).isEqualTo("cache-test-ai-model");

        // Cleanup
        restTemplate.exchange("/api/v1/ai-models/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Map.class);

        // Verify cache is evicted after delete
        Object afterDelete = cacheManager.getCache("ai-models").get(id);
        assertThat(afterDelete).isNull();
    }
}
