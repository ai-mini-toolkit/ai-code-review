package com.aicodereview.api.e2e;

import com.aicodereview.api.e2e.support.MockWebhookServer;
import com.aicodereview.api.e2e.support.TestDataFactory;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.ReviewResultRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.ReviewTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E framework validation tests — Story 9.1 AC #5.
 *
 * <p>Verifies that the E2E test infrastructure itself works correctly:</p>
 * <ul>
 *   <li>TestContainers starts isolated PostgreSQL and Redis</li>
 *   <li>Flyway migrations apply successfully to the test database</li>
 *   <li>Redis connection is alive</li>
 *   <li>TestDataFactory creates entities without errors</li>
 *   <li>MockWebhookServer sends requests that are accepted by the API</li>
 * </ul>
 *
 * <p>These tests use the {@code e2e} Spring profile with dynamic container
 * ports, so they never interfere with the dev environment.</p>
 *
 * @since 9.1.0
 */
@DisplayName("E2E Framework Setup Validation Tests")
class E2EFrameworkSetupTest extends AbstractE2ETest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ReviewResultRepository reviewResultRepository;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @Autowired
    private TestDataFactory testDataFactory;

    private MockWebhookServer mockWebhookServer;

    private static final String GITHUB_REPO_URL =
            "https://github.com/e2e-test-org/framework-setup-repo";

    @BeforeEach
    void setUp() {
        mockWebhookServer = new MockWebhookServer(
                restTemplate,
                TestDataFactory.DEFAULT_GITHUB_SECRET,
                TestDataFactory.DEFAULT_GITLAB_TOKEN
        );
        // Clean state before each test — order respects FK constraints:
        // review_result → review_task → project
        reviewResultRepository.deleteAll();
        reviewTaskRepository.deleteAll();
        projectRepository.deleteAll();
    }

    // ─── Container health ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PostgreSQL container is running and reachable")
    void postgresContainerIsRunning() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(POSTGRES.getJdbcUrl()).startsWith("jdbc:postgresql://");
    }

    @Test
    @DisplayName("Redis container is running and reachable")
    void redisContainerIsRunning() {
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(REDIS.getMappedPort(6379)).isGreaterThan(0);
    }

    // ─── Database connectivity ────────────────────────────────────────────────

    @Test
    @DisplayName("Flyway migrations applied — project table exists")
    void flywayMigrationsApplied() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("Core tables exist after Flyway migration")
    void coreTablesExist() {
        List<String> expectedTables = List.of(
                "project", "review_task", "review_result", "ai_model_config",
                "prompt_template", "notification_template");

        for (String table : expectedTables) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count)
                    .as("Table '%s' should exist", table)
                    .isEqualTo(1);
        }
    }

    // ─── Redis connectivity ───────────────────────────────────────────────────

    @Test
    @DisplayName("Redis PING succeeds")
    void redisPingSucceeds() {
        // Use execute(RedisCallback) so the connection is automatically returned to the pool
        String pong = redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
        assertThat(pong).isEqualToIgnoringCase("PONG");
    }

    @Test
    @DisplayName("Redis read/write cycle works")
    void redisReadWriteCycle() {
        String key = "e2e:framework:test";
        redisTemplate.opsForValue().set(key, "framework-ok");
        Object value = redisTemplate.opsForValue().get(key);
        assertThat(value).isEqualTo("framework-ok");
        redisTemplate.delete(key);
    }

    // ─── TestDataFactory ──────────────────────────────────────────────────────

    @Test
    @DisplayName("TestDataFactory creates GitHub project successfully")
    void testDataFactoryCreatesProject() {
        Project project = testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        assertThat(project.getId()).isNotNull();
        assertThat(project.getRepoUrl()).isEqualTo(GITHUB_REPO_URL);
        assertThat(project.getGitPlatform()).isEqualTo("github");
        assertThat(project.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("TestDataFactory creates pending push task successfully")
    void testDataFactoryCreatesPushTask() {
        Project project = testDataFactory.createGitHubProject(GITHUB_REPO_URL);
        ReviewTask task = testDataFactory.createPendingPushTask(project, "main", "abc123e2e");

        assertThat(task.getId()).isNotNull();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getCommitHash()).isEqualTo("abc123e2e");
        assertThat(task.getProject().getId()).isEqualTo(project.getId());
    }

    // ─── MockWebhookServer ────────────────────────────────────────────────────

    @Test
    @DisplayName("MockWebhookServer sends valid GitHub push webhook — returns 202 Accepted")
    void mockWebhookServerSendsGitHubPush() {
        // Given: a configured GitHub project
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        // When: webhook is sent
        ResponseEntity<String> response = mockWebhookServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "framework001");

        // Then: API accepts the webhook
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("MockWebhookServer computes correct HMAC signature — invalid secret returns 401/403")
    void mockWebhookServerInvalidSecretRejected() {
        // Given: a configured GitHub project
        testDataFactory.createGitHubProject(GITHUB_REPO_URL);

        // When: webhook sent with wrong secret
        MockWebhookServer badServer = new MockWebhookServer(
                restTemplate, "wrong-secret", TestDataFactory.DEFAULT_GITLAB_TOKEN);
        ResponseEntity<String> response = badServer
                .sendGitHubPushEvent(GITHUB_REPO_URL, "main", "framework002");

        // Then: API rejects the request
        assertThat(response.getStatusCode().value())
                .as("Invalid HMAC should be rejected with 4xx")
                .isGreaterThanOrEqualTo(400)
                .isLessThan(500);
    }
}
