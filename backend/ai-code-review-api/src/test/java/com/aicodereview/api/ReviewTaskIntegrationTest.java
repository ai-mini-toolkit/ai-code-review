package com.aicodereview.api;

import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ReviewTask creation from webhook events.
 * <p>
 * Tests the complete flow: webhook reception → task creation → database persistence.
 * Uses real Spring Boot context, PostgreSQL database, and HTTP requests.
 * </p>
 *
 * @since 2.5.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ReviewTask Integration Tests")
class ReviewTaskIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    private static final String GITHUB_REPO_URL = "https://github.com/integration-test/repo";
    private static final String GITLAB_REPO_URL = "https://gitlab.com/integration-test/repo";

    private static boolean setupComplete = false;

    @BeforeEach
    void setUp() {
        // Configure RestTemplate error handler
        restTemplate.getRestTemplate().setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });

        // Set up test projects once
        if (!setupComplete) {
            // Clean up existing data
            reviewTaskRepository.deleteAll();
            projectRepository.deleteAll();

            // Create test projects
            Project githubProject = Project.builder()
                    .name("Integration Test GitHub Repo")
                    .description("Test project for integration tests")
                    .enabled(true)
                    .gitPlatform("github")
                    .repoUrl(GITHUB_REPO_URL)
                    .webhookSecret("integration-test-secret")
                    .build();
            projectRepository.save(githubProject);

            Project gitlabProject = Project.builder()
                    .name("Integration Test GitLab Repo")
                    .description("Test project for GitLab integration tests")
                    .enabled(true)
                    .gitPlatform("gitlab")
                    .repoUrl(GITLAB_REPO_URL)
                    .webhookSecret("integration-test-token")
                    .build();
            projectRepository.save(gitlabProject);

            setupComplete = true;
        }
    }

    @Test
    @DisplayName("GitHub Push webhook should create PUSH task with NORMAL priority")
    void testCreateTaskFromWebhook_GitHub_Push() {
        // Given: GitHub push webhook payload
        String payload = String.format(
                "{\"ref\":\"refs/heads/feature/test\"," +
                "\"after\":\"push123abc\"," +
                "\"repository\":{\"name\":\"repo\",\"full_name\":\"integration-test/repo\",\"html_url\":\"%s\"}," +
                "\"pusher\":{\"name\":\"developer\"}}",
                GITHUB_REPO_URL
        );

        String signature = calculateGitHubSignature(payload, "integration-test-secret");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", signature);

        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        // When: Send webhook
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/webhook/github",
                request,
                String.class
        );

        // Then: Webhook accepted
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: Task created in database
        List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(GITHUB_REPO_URL);
        ReviewTask pushTask = tasks.stream()
                .filter(t -> "push123abc".equals(t.getCommitHash()))
                .findFirst()
                .orElse(null);

        assertThat(pushTask).isNotNull();
        assertThat(pushTask.getTaskType()).isEqualTo(TaskType.PUSH);
        assertThat(pushTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(pushTask.getPriority()).isEqualTo(TaskPriority.NORMAL);
        assertThat(pushTask.getBranch()).isEqualTo("feature/test");
        assertThat(pushTask.getCommitHash()).isEqualTo("push123abc");
        assertThat(pushTask.getAuthor()).isEqualTo("developer");
        assertThat(pushTask.getRetryCount()).isEqualTo(0);
        assertThat(pushTask.getMaxRetries()).isEqualTo(3);
        assertThat(pushTask.getCreatedAt()).isNotNull();
        assertThat(pushTask.getPrNumber()).isNull();
    }

    @Test
    @DisplayName("GitHub Pull Request webhook should create PR task with HIGH priority")
    void testCreateTaskFromWebhook_GitHub_PullRequest() {
        // Given: GitHub pull request webhook payload
        String payload = String.format(
                "{\"action\":\"opened\"," +
                "\"pull_request\":{" +
                    "\"number\":123," +
                    "\"title\":\"Add new feature\"," +
                    "\"body\":\"This PR adds a new feature for testing\"," +
                    "\"user\":{\"login\":\"contributor\"}," +
                    "\"head\":{\"ref\":\"feature-branch\",\"sha\":\"pr456def\"}" +
                "}," +
                "\"repository\":{\"name\":\"repo\",\"full_name\":\"integration-test/repo\",\"html_url\":\"%s\"}}",
                GITHUB_REPO_URL
        );

        String signature = calculateGitHubSignature(payload, "integration-test-secret");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", signature);

        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        // When: Send webhook
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/webhook/github",
                request,
                String.class
        );

        // Then: Webhook accepted
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: PR task created with HIGH priority
        List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(GITHUB_REPO_URL);
        ReviewTask prTask = tasks.stream()
                .filter(t -> t.getTaskType() == TaskType.PULL_REQUEST)
                .filter(t -> t.getPrNumber() != null && t.getPrNumber() == 123)
                .findFirst()
                .orElse(null);

        assertThat(prTask).isNotNull();
        assertThat(prTask.getTaskType()).isEqualTo(TaskType.PULL_REQUEST);
        assertThat(prTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(prTask.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(prTask.getBranch()).isEqualTo("feature-branch");
        assertThat(prTask.getCommitHash()).isEqualTo("pr456def");
        assertThat(prTask.getAuthor()).isEqualTo("contributor");
        assertThat(prTask.getPrNumber()).isEqualTo(123);
        assertThat(prTask.getPrTitle()).isEqualTo("Add new feature");
        assertThat(prTask.getPrDescription()).isEqualTo("This PR adds a new feature for testing");
        assertThat(prTask.getRetryCount()).isEqualTo(0);
        assertThat(prTask.getMaxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("GitLab Merge Request webhook should create MR task with HIGH priority")
    void testCreateTaskFromWebhook_GitLab_MergeRequest() {
        // Given: GitLab merge request webhook payload
        String payload = String.format(
                "{\"object_kind\":\"merge_request\"," +
                "\"user_username\":\"gitlab-user\"," +
                "\"merge_request\":{" +
                    "\"iid\":456," +
                    "\"title\":\"Fix critical bug\"," +
                    "\"description\":\"This MR fixes a critical bug\"," +
                    "\"source_branch\":\"bugfix/critical\"," +
                    "\"last_commit\":{\"id\":\"mr789xyz\"}" +
                "}," +
                "\"project\":{\"name\":\"repo\",\"path_with_namespace\":\"integration-test/repo\",\"web_url\":\"%s\"}}",
                GITLAB_REPO_URL
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gitlab-Token", "integration-test-token");

        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        // When: Send webhook
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/webhook/gitlab",
                request,
                String.class
        );

        // Then: Webhook accepted
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: MR task created with HIGH priority
        List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(GITLAB_REPO_URL);
        ReviewTask mrTask = tasks.stream()
                .filter(t -> t.getTaskType() == TaskType.MERGE_REQUEST)
                .filter(t -> t.getPrNumber() != null && t.getPrNumber() == 456)
                .findFirst()
                .orElse(null);

        assertThat(mrTask).isNotNull();
        assertThat(mrTask.getTaskType()).isEqualTo(TaskType.MERGE_REQUEST);
        assertThat(mrTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(mrTask.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(mrTask.getBranch()).isEqualTo("bugfix/critical");
        assertThat(mrTask.getCommitHash()).isEqualTo("mr789xyz");
        assertThat(mrTask.getAuthor()).isEqualTo("gitlab-user");
        assertThat(mrTask.getPrNumber()).isEqualTo(456);
        assertThat(mrTask.getPrTitle()).isEqualTo("Fix critical bug");
        assertThat(mrTask.getPrDescription()).isEqualTo("This MR fixes a critical bug");
    }

    @Test
    @DisplayName("Task lifecycle: PENDING → RUNNING → COMPLETED")
    void testTaskLifecycle() {
        // Given: Create a task via webhook
        String payload = String.format(
                "{\"ref\":\"refs/heads/test-lifecycle\"," +
                "\"after\":\"lifecycle123\"," +
                "\"repository\":{\"name\":\"repo\",\"full_name\":\"integration-test/repo\",\"html_url\":\"%s\"}," +
                "\"pusher\":{\"name\":\"tester\"}}",
                GITHUB_REPO_URL
        );

        String signature = calculateGitHubSignature(payload, "integration-test-secret");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", signature);

        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        restTemplate.postForEntity("/api/webhook/github", request, String.class);

        // Find the created task
        List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(GITHUB_REPO_URL);
        ReviewTask task = tasks.stream()
                .filter(t -> "lifecycle123".equals(t.getCommitHash()))
                .findFirst()
                .orElseThrow();

        Long taskId = task.getId();

        // When: Mark as RUNNING
        task.setStatus(TaskStatus.RUNNING);
        reviewTaskRepository.save(task);

        // Then: Status updated
        ReviewTask runningTask = reviewTaskRepository.findById(taskId).orElseThrow();
        assertThat(runningTask.getStatus()).isEqualTo(TaskStatus.RUNNING);

        // When: Mark as COMPLETED
        runningTask.setStatus(TaskStatus.COMPLETED);
        reviewTaskRepository.save(runningTask);

        // Then: Status updated to COMPLETED
        ReviewTask completedTask = reviewTaskRepository.findById(taskId).orElseThrow();
        assertThat(completedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("Query tasks by project ID returns all tasks for project")
    void testGetTasksByProjectId() {
        // Given: Find the test project
        Project project = projectRepository.findByRepoUrl(GITHUB_REPO_URL).orElseThrow();

        // When: Query tasks by project ID
        List<ReviewTask> tasks = reviewTaskRepository.findByProjectId(project.getId());

        // Then: Should return multiple tasks from previous tests
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).allMatch(t -> t.getProject().getId().equals(project.getId()));
    }

    /**
     * Helper method to calculate HMAC-SHA256 signature for GitHub webhooks.
     */
    private String calculateGitHubSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hmacBytes) {
                hex.append(String.format("%02x", b));
            }
            return "sha256=" + hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
