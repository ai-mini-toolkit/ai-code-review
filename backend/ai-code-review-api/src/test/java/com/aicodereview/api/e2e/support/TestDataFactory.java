package com.aicodereview.api.e2e.support;

import com.aicodereview.common.enums.TaskPriority;
import com.aicodereview.common.enums.TaskStatus;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.repository.AiModelConfigRepository;
import com.aicodereview.repository.ProjectRepository;
import com.aicodereview.repository.PromptTemplateRepository;
import com.aicodereview.repository.ReviewResultRepository;
import com.aicodereview.repository.ReviewTaskRepository;
import com.aicodereview.repository.entity.AiModelConfig;
import com.aicodereview.repository.entity.Project;
import com.aicodereview.repository.entity.PromptTemplate;
import com.aicodereview.repository.entity.ReviewTask;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for creating repeatable test data in E2E tests.
 *
 * <p>Centralises entity creation so every E2E test uses consistent,
 * predictable data without duplicating builder boilerplate.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * &#64;Autowired TestDataFactory factory;
 *
 * Project p = factory.createGitHubProject("https://github.com/org/repo");
 * ReviewTask t = factory.createPendingPushTask(p, "main", "abc123");
 * </pre>
 *
 * @since 9.1.0
 */
@Component
public class TestDataFactory {

    /** Default GitHub webhook secret — matches application.yml default and MockWebhookServer. */
    public static final String DEFAULT_GITHUB_SECRET = "test-github-secret";

    /** Default GitLab webhook token — matches application.yml default. */
    public static final String DEFAULT_GITLAB_TOKEN  = "test-gitlab-token";

    /** Unique suffix generator to avoid unique-constraint violations across tests. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    private final ProjectRepository projectRepository;
    private final AiModelConfigRepository aiModelConfigRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewResultRepository reviewResultRepository;
    private final PromptTemplateRepository promptTemplateRepository;

    public TestDataFactory(ProjectRepository projectRepository,
                           AiModelConfigRepository aiModelConfigRepository,
                           ReviewTaskRepository reviewTaskRepository,
                           ReviewResultRepository reviewResultRepository,
                           PromptTemplateRepository promptTemplateRepository) {
        this.projectRepository = projectRepository;
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewResultRepository = reviewResultRepository;
        this.promptTemplateRepository = promptTemplateRepository;
    }

    // ─── Projects ─────────────────────────────────────────────────────────────

    /**
     * Creates and saves a GitHub project with the default webhook secret.
     *
     * @param repoHtmlUrl full HTML URL, e.g. {@code https://github.com/org/repo}
     */
    public Project createGitHubProject(String repoHtmlUrl) {
        int seq = SEQUENCE.getAndIncrement();
        Project project = Project.builder()
                .name("E2E GitHub Project #" + seq)
                .description("E2E test project for GitHub")
                .enabled(true)
                .gitPlatform("github")
                .repoUrl(repoHtmlUrl)
                .webhookSecret(DEFAULT_GITHUB_SECRET)
                .build();
        return Objects.requireNonNull(projectRepository.save(project));
    }

    /**
     * Creates and saves a GitLab project with the default webhook token.
     *
     * @param repoWebUrl full web URL, e.g. {@code https://gitlab.com/org/repo}
     */
    public Project createGitLabProject(String repoWebUrl) {
        int seq = SEQUENCE.getAndIncrement();
        Project project = Project.builder()
                .name("E2E GitLab Project #" + seq)
                .description("E2E test project for GitLab")
                .enabled(true)
                .gitPlatform("gitlab")
                .repoUrl(repoWebUrl)
                .webhookSecret(DEFAULT_GITLAB_TOKEN)
                .build();
        return Objects.requireNonNull(projectRepository.save(project));
    }

    /**
     * Creates and saves an AWS CodeCommit project.
     *
     * <p>For CodeCommit, the {@code repoUrl} stored in the Project is the repository
     * name (as extracted by {@code WebhookController} from the inner SNS Message JSON).
     * Pass the same repository name to {@code MockWebhookServer.sendCodeCommitPushEvent}.</p>
     *
     * @param repoName the CodeCommit repository name (e.g. {@code "my-service-repo"})
     */
    public Project createCodeCommitProject(String repoName) {
        int seq = SEQUENCE.getAndIncrement();
        Project project = Project.builder()
                .name("E2E CodeCommit Project #" + seq)
                .description("E2E test project for AWS CodeCommit")
                .enabled(true)
                .gitPlatform("codecommit")
                .repoUrl(repoName)
                .webhookSecret("") // CodeCommit uses SNS, no shared secret
                .build();
        return Objects.requireNonNull(projectRepository.save(project));
    }

    // ─── AI Model Configs ─────────────────────────────────────────────────────

    /**
     * Creates and saves a mock OpenAI model configuration.
     * API key is a test placeholder — real calls are intercepted by {@link MockAIProvider}.
     */
    public AiModelConfig createMockOpenAIConfig() {
        int seq = SEQUENCE.getAndIncrement();
        AiModelConfig config = AiModelConfig.builder()
                .name("E2E Mock OpenAI #" + seq)
                .provider("openai")
                .modelName("gpt-4o")
                .apiKey("sk-e2e-test-key-placeholder")
                .apiEndpoint("https://api.openai.com/v1")
                .temperature(new BigDecimal("0.30"))
                .maxTokens(4000)
                .timeoutSeconds(30)
                .enabled(true)
                .build();
        return Objects.requireNonNull(aiModelConfigRepository.save(config));
    }

    // ─── Review Tasks ─────────────────────────────────────────────────────────

    /**
     * Creates and saves a PENDING push review task linked to the given project.
     *
     * @param project   associated project
     * @param branch    branch name
     * @param commitSha commit SHA
     */
    public ReviewTask createPendingPushTask(Project project, String branch, String commitSha) {
        ReviewTask task = ReviewTask.builder()
                .project(project)
                .taskType(TaskType.PUSH)
                .repoUrl(project.getRepoUrl())
                .branch(branch)
                .commitHash(commitSha)
                .author("e2e-test-user")
                .status(TaskStatus.PENDING)
                .priority(TaskPriority.NORMAL)
                .retryCount(0)
                .maxRetries(3)
                .build();
        return Objects.requireNonNull(reviewTaskRepository.save(task));
    }

    /**
     * Creates and saves a PENDING pull-request review task linked to the given project.
     *
     * @param project   associated project
     * @param branch    source branch name
     * @param commitSha head commit SHA
     * @param prNumber  pull request number
     */
    public ReviewTask createPendingPRTask(Project project, String branch,
                                          String commitSha, int prNumber) {
        ReviewTask task = ReviewTask.builder()
                .project(project)
                .taskType(TaskType.PULL_REQUEST)
                .repoUrl(project.getRepoUrl())
                .branch(branch)
                .commitHash(commitSha)
                .author("e2e-test-user")
                .prNumber(prNumber)
                .prTitle("E2E Test PR #" + prNumber)
                .prDescription("Automated E2E test pull request")
                .status(TaskStatus.PENDING)
                .priority(TaskPriority.HIGH)
                .retryCount(0)
                .maxRetries(3)
                .build();
        return Objects.requireNonNull(reviewTaskRepository.save(task));
    }

    // ─── Prompt Templates ─────────────────────────────────────────────────────

    /**
     * Creates and saves a minimal "code-review" prompt template required by
     * {@code ReviewOrchestrator} during full-pipeline E2E tests.
     *
     * <p>The template content uses Handlebars triple-stache syntax so the
     * raw diff is inserted verbatim without HTML-escaping.</p>
     */
    public PromptTemplate createCodeReviewPromptTemplate() {
        int seq = SEQUENCE.getAndIncrement();
        PromptTemplate template = PromptTemplate.builder()
                .name("E2E Code Review Template #" + seq)
                .category("code-review")
                .templateContent(
                        "Review the following diff and provide structured feedback.\n\n" +
                        "Diff:\n{{{rawDiff}}}\n\n" +
                        "Files: {{{files}}}\n" +
                        "Statistics: {{{statistics}}}")
                .version(1)
                .enabled(true)
                .build();
        return Objects.requireNonNull(promptTemplateRepository.save(template));
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    /**
     * Deletes all test data in FK-safe order:
     * review_result → review_task → ai_model_config → project → prompt_template.
     */
    public void deleteAll() {
        reviewResultRepository.deleteAll();
        reviewTaskRepository.deleteAll();
        aiModelConfigRepository.deleteAll();
        projectRepository.deleteAll();
        promptTemplateRepository.deleteAll();
    }
}
