package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.auth.LoginRequest;
import com.aicodereview.common.dto.auth.LoginResult;
import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewMetadata;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.threshold.ThresholdConfigDTO;
import com.aicodereview.common.dto.threshold.ThresholdRuleDTO;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.ReviewResultRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.UserRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import com.aicodereview.repository.entity.User;
import com.aicodereview.service.ProjectService;
import com.aicodereview.service.ReviewResultService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ReviewResultController.
 * Tests all review result query endpoints with a real database.
 *
 * @since 5.3.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReviewResultControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static Long projectId1;
    private static Long projectId2;
    private static Long taskId1; // successful review
    private static Long taskId2; // failed review
    private static Long taskId3; // successful review on project2
    private static String adminToken;
    private static boolean authSetupDone = false;

    @BeforeAll
    static void setupTestData(
            @Autowired ProjectRepository projectRepository,
            @Autowired ReviewTaskRepository reviewTaskRepository,
            @Autowired ReviewResultRepository reviewResultRepository,
            @Autowired ReviewResultService reviewResultService) {

        // Clean up (CASCADE deletes review_task and review_result)
        reviewResultRepository.deleteAll();
        reviewTaskRepository.deleteAll();
        projectRepository.deleteAll();

        // Create two projects
        Project project1 = projectRepository.save(Project.builder()
                .name("Integration Test Project A")
                .repoUrl("https://github.com/test/int-a")
                .gitPlatform("GitHub")
                .webhookSecret("test-secret-a")
                .enabled(true)
                .build());
        projectId1 = project1.getId();

        Project project2 = projectRepository.save(Project.builder()
                .name("Integration Test Project B")
                .repoUrl("https://github.com/test/int-b")
                .gitPlatform("GitHub")
                .webhookSecret("test-secret-b")
                .enabled(true)
                .build());
        projectId2 = project2.getId();

        // Create tasks in RUNNING state (required by saveResult)
        ReviewTask task1 = reviewTaskRepository.save(ReviewTask.builder()
                .project(project1).repoUrl(project1.getRepoUrl())
                .branch("main").commitHash("abc123").author("dev1@test.com")
                .taskType(TaskType.PUSH).priority(TaskPriority.NORMAL)
                .status(TaskStatus.RUNNING).retryCount(0).maxRetries(3)
                .build());
        taskId1 = task1.getId();

        ReviewTask task2 = reviewTaskRepository.save(ReviewTask.builder()
                .project(project1).repoUrl(project1.getRepoUrl())
                .branch("feature").commitHash("def456").author("dev2@test.com")
                .taskType(TaskType.PULL_REQUEST).priority(TaskPriority.HIGH)
                .status(TaskStatus.RUNNING).retryCount(0).maxRetries(3)
                .build());
        taskId2 = task2.getId();

        ReviewTask task3 = reviewTaskRepository.save(ReviewTask.builder()
                .project(project2).repoUrl(project2.getRepoUrl())
                .branch("develop").commitHash("ghi789").author("dev3@test.com")
                .taskType(TaskType.PUSH).priority(TaskPriority.NORMAL)
                .status(TaskStatus.RUNNING).retryCount(0).maxRetries(3)
                .build());
        taskId3 = task3.getId();

        // Save successful review result for task1
        List<ReviewIssue> issues = List.of(
                ReviewIssue.builder()
                        .severity(IssueSeverity.CRITICAL).category(IssueCategory.SECURITY)
                        .filePath("UserService.java").line(42)
                        .message("SQL injection vulnerability")
                        .suggestion("Use PreparedStatement")
                        .build(),
                ReviewIssue.builder()
                        .severity(IssueSeverity.HIGH).category(IssueCategory.PERFORMANCE)
                        .filePath("Controller.java").line(10)
                        .message("N+1 query detected")
                        .suggestion("Use batch query")
                        .build()
        );
        ReviewMetadata metadata = ReviewMetadata.builder()
                .providerId("anthropic").model("claude-sonnet")
                .promptTokens(1000).completionTokens(500).durationMs(2000L)
                .degradationEvents(List.of())
                .build();
        reviewResultService.saveResult(taskId1, ReviewResult.success(issues, metadata));

        // Save failed review result for task2
        reviewResultService.saveResult(taskId2, ReviewResult.failed("API timeout: 30s exceeded"));

        // Save successful review result for task3 (project2)
        reviewResultService.saveResult(taskId3, ReviewResult.success(
                List.of(ReviewIssue.builder()
                        .severity(IssueSeverity.LOW).category(IssueCategory.STYLE)
                        .filePath("App.java").line(5)
                        .message("Inconsistent naming")
                        .suggestion("Use camelCase")
                        .build()),
                ReviewMetadata.builder()
                        .providerId("openai").model("gpt-4")
                        .promptTokens(500).completionTokens(200).durationMs(1500L)
                        .degradationEvents(List.of())
                        .build()));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(Map<String, Object> body) {
        return (Map<String, Object>) body.get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getError(Map<String, Object> body) {
        return (Map<String, Object>) body.get("error");
    }

    // ===== GET /{taskId}/result =====

    @Test
    @Order(1)
    @DisplayName("GET /result should return complete review result")
    void shouldGetReviewResult() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/" + taskId1 + "/result", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(((Number) data.get("taskId")).longValue()).isEqualTo(taskId1);
        assertThat(data.get("success")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) data.get("issues");
        assertThat(issues).hasSize(2);
        assertThat(issues.get(0).get("severity")).isEqualTo("CRITICAL");

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) data.get("statistics");
        assertThat(((Number) stats.get("total")).intValue()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertThat(metadata.get("providerId")).isEqualTo("anthropic");
    }

    @Test
    @Order(2)
    @DisplayName("GET /result should return failed review result with error")
    void shouldGetFailedReviewResult() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/" + taskId2 + "/result", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = getData(response.getBody());
        assertThat(data.get("success")).isEqualTo(false);
        assertThat(data.get("errorMessage")).isEqualTo("API timeout: 30s exceeded");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) data.get("issues");
        assertThat(issues).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("GET /result should return 404 for non-existent task")
    void shouldReturn404ForNonExistentResult() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/999999/result", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }

    // ===== GET /{taskId}/report =====

    @Test
    @Order(10)
    @DisplayName("GET /report should return JSON report by default")
    void shouldGetJsonReport() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/" + taskId1 + "/report", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(((Number) data.get("taskId")).longValue()).isEqualTo(taskId1);
        assertThat(data.get("projectName")).isEqualTo("Integration Test Project A");
        assertThat(data.get("branch")).isEqualTo("main");
        assertThat(data.get("author")).isEqualTo("dev1@test.com");
        assertThat(data.containsKey("issuesByFile")).isTrue();
        assertThat(data.containsKey("issuesBySeverity")).isTrue();
        assertThat(data.containsKey("issuesByCategory")).isTrue();
    }

    @Test
    @Order(11)
    @DisplayName("GET /report?format=json should return JSON report explicitly")
    void shouldGetJsonReportExplicit() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/" + taskId1 + "/report?format=json", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(true);
        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();
        assertThat(data.get("projectName")).isEqualTo("Integration Test Project A");
    }

    @Test
    @Order(12)
    @DisplayName("GET /report?format=markdown should return Markdown text")
    void shouldGetMarkdownReport() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/reviews/" + taskId1 + "/report?format=markdown", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/plain");

        String markdown = response.getBody();
        assertThat(markdown).isNotNull();
        assertThat(markdown).contains("# Code Review Report");
        assertThat(markdown).contains("**Project:** Integration Test Project A");
        assertThat(markdown).contains("**Branch:** main");
        assertThat(markdown).contains("**Status:** Passed");
        assertThat(markdown).contains("SQL injection vulnerability");
    }

    @Test
    @Order(13)
    @DisplayName("GET /report?format=html should return HTML page")
    void shouldGetHtmlReport() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/reviews/" + taskId1 + "/report?format=html", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/html");

        String html = response.getBody();
        assertThat(html).isNotNull();
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("Code Review Report");
        assertThat(html).contains("Integration Test Project A");
        assertThat(html).contains("</html>");
    }

    @Test
    @Order(14)
    @DisplayName("GET /report?format=pdf should return 400 Bad Request")
    void shouldReturn400ForInvalidFormat() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/" + taskId1 + "/report?format=pdf", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }

    @Test
    @Order(15)
    @DisplayName("GET /report should return 404 for non-existent task")
    void shouldReturn404ForNonExistentReport() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/999999/report", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }

    // ===== GET / (paginated list) =====

    @Test
    @Order(20)
    @DisplayName("GET / should return paginated list of all reviews")
    void shouldListAllReviews() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews?page=0&size=20&sort=createdAt,desc", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        Map<String, Object> data = getData(response.getBody());
        assertThat(data).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
        assertThat(content).hasSize(3);

        // Verify lightweight summary fields present
        Map<String, Object> firstItem = content.get(0);
        assertThat(firstItem.containsKey("resultId")).isTrue();
        assertThat(firstItem.containsKey("taskId")).isTrue();
        assertThat(firstItem.containsKey("projectName")).isTrue();
        assertThat(firstItem.containsKey("branch")).isTrue();
        assertThat(firstItem.containsKey("author")).isTrue();
        assertThat(firstItem.containsKey("success")).isTrue();
        assertThat(firstItem.containsKey("totalIssues")).isTrue();
        assertThat(firstItem.containsKey("createdAt")).isTrue();

        // Verify sort order: createdAt DESC — last created (task3) should be first
        String firstCreatedAt = (String) content.get(0).get("createdAt");
        String lastCreatedAt = (String) content.get(2).get("createdAt");
        assertThat(firstCreatedAt.compareTo(lastCreatedAt)).isGreaterThanOrEqualTo(0);

        // Verify pagination metadata
        assertThat(((Number) data.get("totalElements")).intValue()).isEqualTo(3);
        assertThat(((Number) data.get("totalPages")).intValue()).isEqualTo(1);
        assertThat(((Number) data.get("number")).intValue()).isEqualTo(0);
        assertThat(((Number) data.get("size")).intValue()).isEqualTo(20);
    }

    @Test
    @Order(21)
    @DisplayName("GET /?projectId=X should filter by project")
    void shouldFilterByProjectId() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews?projectId=" + projectId1, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = getData(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
        assertThat(content).hasSize(2);

        // All results should be from project A
        for (Map<String, Object> item : content) {
            assertThat(item.get("projectName")).isEqualTo("Integration Test Project A");
        }
    }

    @Test
    @Order(22)
    @DisplayName("GET /?success=true should filter by success status")
    void shouldFilterBySuccess() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews?success=true", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = getData(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
        assertThat(content).hasSize(2);

        for (Map<String, Object> item : content) {
            assertThat(item.get("success")).isEqualTo(true);
        }
    }

    @Test
    @Order(23)
    @DisplayName("GET /?projectId=X&success=false should filter by both")
    void shouldFilterByProjectIdAndSuccess() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews?projectId=" + projectId1 + "&success=false", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = getData(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
        assertThat(content).hasSize(1);

        assertThat(content.get(0).get("projectName")).isEqualTo("Integration Test Project A");
        assertThat(content.get(0).get("success")).isEqualTo(false);
        assertThat(content.get(0).get("errorMessage")).isEqualTo("API timeout: 30s exceeded");
    }

    @Test
    @Order(24)
    @DisplayName("GET /?page=0&size=2 should respect page size")
    void shouldRespectPageSize() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews?page=0&size=2", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = getData(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
        assertThat(content).hasSize(2);

        assertThat(((Number) data.get("totalElements")).intValue()).isEqualTo(3);
        assertThat(((Number) data.get("totalPages")).intValue()).isEqualTo(2);
    }

    @Test
    @Order(25)
    @DisplayName("GET /?projectId=999 should return empty page for non-existent project")
    void shouldReturnEmptyForNonExistentProject() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews?projectId=999999", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = getData(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
        assertThat(content).isEmpty();
        assertThat(((Number) data.get("totalElements")).intValue()).isEqualTo(0);
    }

    // ===== Threshold Validation Integration (Story 6.2) =====

    @Test
    @Order(30)
    @DisplayName("GET /result should include thresholdResult for review with disabled thresholds")
    void shouldIncludeThresholdResultForDisabledThresholds() {
        // Projects created in @BeforeAll have default thresholds (enabled=false)
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/" + taskId1 + "/result", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = getData(response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> thresholdResult = (Map<String, Object>) data.get("thresholdResult");
        assertThat(thresholdResult).isNotNull();
        assertThat(thresholdResult.get("passed")).isEqualTo(true);
        assertThat((List<?>) thresholdResult.get("violations")).isEmpty();
        assertThat(thresholdResult.get("action")).isNull();
    }

    @Test
    @Order(31)
    @DisplayName("Should persist threshold violations for project with enabled thresholds")
    @SuppressWarnings("unchecked")
    void shouldPersistThresholdViolationsForEnabledThresholds(
            @Autowired ProjectService projectService,
            @Autowired ProjectRepository projectRepository,
            @Autowired ReviewTaskRepository reviewTaskRepository,
            @Autowired ReviewResultService reviewResultService) {

        // Create project with strict thresholds enabled
        Project project = projectRepository.save(Project.builder()
                .name("Threshold Test Project")
                .repoUrl("https://github.com/test/threshold-proj")
                .gitPlatform("GitHub")
                .webhookSecret("thresh-secret")
                .enabled(true)
                .build());

        // Enable thresholds via service (which uses cache-aware method)
        ThresholdConfigDTO thresholdConfig = ThresholdConfigDTO.builder()
                .enabled(true)
                .rules(List.of(
                        ThresholdRuleDTO.builder().severity("CRITICAL").maxCount(0).build(),
                        ThresholdRuleDTO.builder().severity("HIGH").maxCount(2).build(),
                        ThresholdRuleDTO.builder().totalIssues(10).build()
                ))
                .action("BLOCK_MERGE")
                .build();
        projectService.updateThresholds(project.getId(), thresholdConfig);

        // Create a RUNNING task
        ReviewTask task = reviewTaskRepository.save(ReviewTask.builder()
                .project(project).repoUrl(project.getRepoUrl())
                .branch("feature/violations").commitHash("viol123").author("dev@test.com")
                .taskType(TaskType.PULL_REQUEST).priority(TaskPriority.HIGH)
                .status(TaskStatus.RUNNING).retryCount(0).maxRetries(3)
                .build());

        // Save review result with issues that violate thresholds
        List<ReviewIssue> issues = List.of(
                ReviewIssue.builder()
                        .severity(IssueSeverity.CRITICAL).category(IssueCategory.SECURITY)
                        .filePath("Auth.java").line(1).message("Hardcoded password")
                        .suggestion("Use environment variable").build(),
                ReviewIssue.builder()
                        .severity(IssueSeverity.HIGH).category(IssueCategory.PERFORMANCE)
                        .filePath("Db.java").line(10).message("N+1 query")
                        .suggestion("Use JOIN FETCH").build(),
                ReviewIssue.builder()
                        .severity(IssueSeverity.HIGH).category(IssueCategory.PERFORMANCE)
                        .filePath("Db.java").line(20).message("Missing index")
                        .suggestion("Add index").build(),
                ReviewIssue.builder()
                        .severity(IssueSeverity.HIGH).category(IssueCategory.STYLE)
                        .filePath("Code.java").line(5).message("Long method")
                        .suggestion("Refactor").build()
        );
        reviewResultService.saveResult(task.getId(), ReviewResult.success(issues,
                ReviewMetadata.builder().providerId("openai").model("gpt-4")
                        .promptTokens(100).completionTokens(50).durationMs(500L)
                        .degradationEvents(List.of()).build()));

        // Fetch via API and verify threshold result
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/reviews/" + task.getId() + "/result", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = getData(response.getBody());

        Map<String, Object> thresholdResult = (Map<String, Object>) data.get("thresholdResult");
        assertThat(thresholdResult).isNotNull();
        assertThat(thresholdResult.get("passed")).isEqualTo(false);
        assertThat(thresholdResult.get("action")).isEqualTo("BLOCK_MERGE");

        List<Map<String, Object>> violations = (List<Map<String, Object>>) thresholdResult.get("violations");
        assertThat(violations).isNotEmpty();

        // CRITICAL <= 0 violated (actual: 1)
        assertThat(violations.stream().anyMatch(v ->
                "CRITICAL <= 0".equals(v.get("rule"))
                        && ((Number) v.get("actual")).intValue() == 1
                        && ((Number) v.get("threshold")).intValue() == 0
        )).isTrue();

        // HIGH <= 2 violated (actual: 3)
        assertThat(violations.stream().anyMatch(v ->
                "HIGH <= 2".equals(v.get("rule"))
                        && ((Number) v.get("actual")).intValue() == 3
                        && ((Number) v.get("threshold")).intValue() == 2
        )).isTrue();
    }
}
