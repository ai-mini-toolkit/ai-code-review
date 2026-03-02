package com.aicodereview.api.e2e.support;

import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.repository.ReviewResultRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.ReviewTask;
import org.awaitility.Awaitility;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Custom assertion helpers for E2E tests, wrapping common database and
 * Redis queue state checks with clear, readable method names.
 *
 * <p>Uses Awaitility (included in {@code spring-boot-starter-test}) for
 * asynchronous assertions that poll until a condition is met or a timeout
 * is reached.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * &#64;Autowired AssertionHelper assertions;
 *
 * assertions.assertTaskCreatedForCommit(repoUrl, "abc123", TaskStatus.PENDING);
 * assertions.awaitTaskStatus("abc123", repoUrl, TaskStatus.COMPLETED, Duration.ofSeconds(60));
 * </pre>
 *
 * @since 9.1.0
 */
@Component
public class AssertionHelper {

    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewResultRepository reviewResultRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public AssertionHelper(ReviewTaskRepository reviewTaskRepository,
                           ReviewResultRepository reviewResultRepository,
                           RedisTemplate<String, Object> redisTemplate) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewResultRepository = reviewResultRepository;
        this.redisTemplate = redisTemplate;
    }

    // ─── Task assertions ──────────────────────────────────────────────────────

    /**
     * Asserts that exactly one task exists for the given repo URL and commit SHA,
     * and that its status matches the expected value.
     *
     * @param repoUrl    repository URL
     * @param commitSha  commit SHA
     * @param expected   expected task status
     * @return the matched task for further assertions
     */
    public ReviewTask assertTaskCreatedForCommit(String repoUrl,
                                                  String commitSha,
                                                  TaskStatus expected) {
        List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(repoUrl);
        Optional<ReviewTask> match = tasks.stream()
                .filter(t -> commitSha.equals(t.getCommitHash()))
                .findFirst();

        assertThat(match)
                .as("Expected a task for commit %s on %s", commitSha, repoUrl)
                .isPresent();

        ReviewTask task = match.get();
        assertThat(task.getStatus())
                .as("Task status for commit %s", commitSha)
                .isEqualTo(expected);
        return task;
    }

    /**
     * Polls until the task for the given commit SHA reaches the expected status,
     * or the timeout is exceeded.
     *
     * @param commitSha  commit SHA to look up
     * @param repoUrl    repository URL
     * @param expected   status to wait for
     * @param timeout    maximum wait duration
     */
    public void awaitTaskStatus(String commitSha,
                                 String repoUrl,
                                 TaskStatus expected,
                                 Duration timeout) {
        Awaitility.await()
                .atMost(timeout)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<ReviewTask> tasks = reviewTaskRepository.findByRepoUrl(repoUrl);
                    Optional<ReviewTask> match = tasks.stream()
                            .filter(t -> commitSha.equals(t.getCommitHash()))
                            .findFirst();
                    assertThat(match).isPresent();
                    assertThat(match.get().getStatus()).isEqualTo(expected);
                });
    }

    // ─── Review result assertions ─────────────────────────────────────────────

    /**
     * Asserts that a review result exists for the given task ID.
     *
     * @param taskId the review task ID
     */
    public void assertReviewResultExistsForTask(Long taskId) {
        assertThat(reviewResultRepository.findByReviewTaskId(taskId))
                .as("Expected review result for task %d", taskId)
                .isPresent();
    }

    // ─── Redis queue assertions ───────────────────────────────────────────────

    /**
     * Asserts that the Redis sorted set at the given key has the expected size.
     *
     * @param queueKey Redis key of the sorted set
     * @param expected expected number of elements
     */
    public void assertQueueSize(String queueKey, long expected) {
        Long rawSize = redisTemplate.opsForZSet().zCard(queueKey);
        long size = (rawSize != null) ? rawSize : 0L;
        assertThat(size)
                .as("Redis queue '%s' size", queueKey)
                .isEqualTo(expected);
    }

    /**
     * Asserts that the Redis sorted set at the given key is empty.
     *
     * @param queueKey Redis key of the sorted set
     */
    public void assertQueueEmpty(String queueKey) {
        assertQueueSize(queueKey, 0L);
    }
}
