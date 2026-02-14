# Story 4.1: AI 提供商抽象层

Status: done

## Story

As a **system architect**,
I want to **implement an AI provider abstraction layer with strategy + factory pattern**,
so that **multiple AI providers can be swapped, extended, and tested independently, enabling the review engine to support OpenAI, Anthropic, and custom endpoints**.

**Design Philosophy**: This Story establishes the core contracts and data models for Epic 4. No actual HTTP calls or AI API integration — just interfaces, DTOs, enums, exceptions, and the factory. Stories 4.2 and 4.3 implement the concrete providers.

## Acceptance Criteria

1. **AC1: AIProvider Interface** - Create `AIProvider` in integration module (`integration/ai/`):
   - `analyze(CodeContext context, String renderedPrompt): ReviewResult`
   - `isAvailable(): boolean`
   - `getProviderId(): String` (e.g., "openai", "anthropic")
   - `getMaxTokens(): int`
   - NOTE: Second parameter is `String renderedPrompt` (NOT PromptTemplate entity) — service layer renders the template before calling provider

2. **AC2: AIProviderFactory** - Factory in integration module (`integration/ai/`):
   - Constructor injection via `List<AIProvider>` for auto-discovery of `@Component` implementations
   - `getProvider(String providerId): AIProvider` — lookup by provider ID
   - `getDefaultProvider(): AIProvider` — returns provider matching configurable default ID
   - Throws `UnsupportedPlatformException` if provider not found (reuse existing exception)
   - `@Value("${ai.provider.default:openai}")` for default provider configuration

3. **AC3: ReviewResult DTO** - In common module (`dto/review/`):
   - `List<ReviewIssue> issues` — the review findings
   - `ReviewMetadata metadata` — model info, timing, token usage, degradation events
   - `boolean success` — whether the review completed successfully
   - `String errorMessage` — populated on failure (nullable)
   - Static factory: `ReviewResult.success(List<ReviewIssue>, ReviewMetadata)`
   - Static factory: `ReviewResult.failed(String errorMessage)`

4. **AC4: ReviewIssue DTO** - In common module (`dto/review/`):
   - `IssueSeverity severity` — CRITICAL, HIGH, MEDIUM, LOW, INFO
   - `IssueCategory category` — SECURITY, PERFORMANCE, MAINTAINABILITY, CORRECTNESS, STYLE, BEST_PRACTICES
   - `String filePath` — affected file (nullable, for general issues)
   - `Integer line` — affected line number (nullable)
   - `String message` — description of the issue
   - `String suggestion` — recommended fix (nullable)

5. **AC5: ReviewMetadata DTO** - In common module (`dto/review/`):
   - `String providerId` — which AI provider was used (e.g., "openai")
   - `String model` — specific model name (e.g., "gpt-4")
   - `int promptTokens` — input token count
   - `int completionTokens` — output token count
   - `long durationMs` — API call duration in milliseconds
   - `List<String> degradationEvents` — log of fallback attempts (empty if none)

6. **AC6: IssueSeverity Enum** - In common module (`enums/`):
   - Values: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`
   - `int score` field for severity ordering (CRITICAL=5, HIGH=4, MEDIUM=3, LOW=2, INFO=1)

7. **AC7: IssueCategory Enum** - In common module (`enums/`):
   - Values: `SECURITY`, `PERFORMANCE`, `MAINTAINABILITY`, `CORRECTNESS`, `STYLE`, `BEST_PRACTICES`
   - `String displayName` field for human-readable label

8. **AC8: AI Exception Hierarchy** - In common module (`exception/`):
   - `AIProviderException extends RuntimeException` — base, with `statusCode` field (reuse GitApiException pattern)
   - `RateLimitException extends AIProviderException` — 429 errors
   - `AIAuthenticationException extends AIProviderException` — 401 errors
   - `AITimeoutException extends AIProviderException` — timeout errors

9. **AC9: Configuration** - Add to `application.yml`:
   ```yaml
   ai:
     provider:
       default: openai
   ```

10. **AC10: Unit Tests** - Comprehensive tests:
    - AIProviderFactory: test provider lookup, default provider, unknown provider, empty factory
    - ReviewResult: test success/failed factories, JSON serialization round-trip
    - ReviewIssue: test builder, all fields
    - Enum tests: IssueSeverity count+scores, IssueCategory count+displayNames
    - Exception tests: AIProviderException hierarchy, statusCode preservation

## Tasks / Subtasks

- [x] Task 1: Create IssueSeverity enum in common module (AC: #6)
  - [x] 1.1 Create `IssueSeverity.java` in `common/enums/` with 5 values and score field
  - [x] 1.2 Add unit test `IssueSeverityTest` for count and scores

- [x] Task 2: Create IssueCategory enum in common module (AC: #7)
  - [x] 2.1 Create `IssueCategory.java` in `common/enums/` with 6 values and displayName
  - [x] 2.2 Add unit test `IssueCategoryTest` for count and displayNames

- [x] Task 3: Create AI exception hierarchy in common module (AC: #8)
  - [x] 3.1 Create `AIProviderException.java` in `common/exception/` with statusCode (follow GitApiException pattern)
  - [x] 3.2 Create `RateLimitException.java` extending AIProviderException
  - [x] 3.3 Create `AIAuthenticationException.java` extending AIProviderException
  - [x] 3.4 Create `AITimeoutException.java` extending AIProviderException
  - [x] 3.5 Add unit tests for exception hierarchy

- [x] Task 4: Create ReviewIssue, ReviewMetadata, ReviewResult DTOs in common module (AC: #3, #4, #5)
  - [x] 4.1 Create `ReviewIssue.java` in `common/dto/review/` with Lombok @Data @Builder
  - [x] 4.2 Create `ReviewMetadata.java` in `common/dto/review/` with Lombok @Data @Builder
  - [x] 4.3 Create `ReviewResult.java` in `common/dto/review/` with static factories
  - [x] 4.4 Add `ReviewResultTest.java` — test success/failed factories, JSON serialization round-trip

- [x] Task 5: Create AIProvider interface in integration module (AC: #1)
  - [x] 5.1 Create `AIProvider.java` interface in `integration/ai/`
  - [x] 5.2 Define 4 methods: analyze, isAvailable, getProviderId, getMaxTokens

- [x] Task 6: Create AIProviderFactory in integration module (AC: #2)
  - [x] 6.1 Create `AIProviderFactory.java` as `@Component` in `integration/ai/`
  - [x] 6.2 Constructor injection via `List<AIProvider>`, build `Map<String, AIProvider>` by providerId
  - [x] 6.3 Implement `getProvider(String)` and `getDefaultProvider()`
  - [x] 6.4 Add `AIProviderFactoryTest.java` — test lookup, default, unknown provider, empty factory

- [x] Task 7: Add configuration to application.yml (AC: #9)
  - [x] 7.1 Add `ai.provider.default: openai` to application.yml

- [x] Task 8: Verify build and run full test suite (AC: all)
  - [x] 8.1 Run `mvn compile test` across all modules
  - [x] 8.2 Verify no regressions in existing tests
  - [x] 8.3 Verify all new tests pass

## Dev Notes

### Architecture Decisions

**Module Placement (follows established project patterns)**:
- `AIProvider` interface → `integration/ai/` (same pattern as `GitPlatformClient` in `integration/git/`)
- `AIProviderFactory` → `integration/ai/` (same pattern as `GitPlatformClientFactory`)
- `ReviewResult`, `ReviewIssue`, `ReviewMetadata` → `common/dto/review/` (following `common/dto/reviewtask/` convention)
- `IssueSeverity`, `IssueCategory` → `common/enums/` (following existing enum pattern)
- `AIProviderException` hierarchy → `common/exception/` (following `GitApiException` pattern)

**Why `String renderedPrompt` instead of `PromptTemplate entity`**:
- `integration` module does NOT depend on `repository` module (where PromptTemplate entity lives)
- Service layer (Story 4.4's ReviewOrchestrator) will load PromptTemplate, render variables, and pass the rendered String
- This maintains clean module separation: integration handles HTTP transport, service handles business orchestration

**Why NOT `service/strategy/`**:
- Architecture doc suggests `service/strategy/` but actual project puts platform abstractions in `integration/` module
- `GitPlatformClient` interface + `GitPlatformClientFactory` + implementations are all in `integration/git/`
- Following the ESTABLISHED project pattern, not the theoretical architecture doc
- AI providers are external integrations (HTTP calls to OpenAI/Anthropic APIs), just like Git platform clients

### Existing Code to Reuse / Integrate With

**GitPlatformClientFactory** (Story 3.2 — EXACT pattern to replicate):
```java
@Component
@Slf4j
public class GitPlatformClientFactory {
    private final Map<GitPlatform, GitPlatformClient> clientMap;

    public GitPlatformClientFactory(List<GitPlatformClient> clients) {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(
                        GitPlatformClient::getPlatform,
                        Function.identity()
                ));
        log.info("Initialized Git platform client factory with {} platform(s)", clientMap.size());
    }

    public GitPlatformClient getClient(GitPlatform platform) {
        GitPlatformClient client = clientMap.get(platform);
        if (client == null) {
            throw new UnsupportedPlatformException("No Git API client registered for platform: " + platform);
        }
        return client;
    }
}
```
- Replicate for AIProviderFactory: `Map<String, AIProvider>` keyed by `getProviderId()`
- Reuse `UnsupportedPlatformException` for unknown provider (or create a specific one if desired)

**GitApiException** (Story 3.2 — pattern for AI exceptions):
```java
public class GitApiException extends RuntimeException {
    private final int statusCode;

    public GitApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() { return statusCode; }
}
```

**CodeContext DTO** (Story 3.3 — the input to AIProvider.analyze()):
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CodeContext {
    private String rawDiff;
    private List<FileInfo> files;
    private Map<String, String> fileContents;
    private DiffStatistics statistics;
    private TaskMetadata taskMeta;
}
```

**AiModelConfig entity** (Story 1.6 — AI model configuration):
- Fields: `name`, `provider`, `modelName`, `apiKey` (encrypted), `apiEndpoint`, `temperature`, `maxTokens`, `timeoutSeconds`, `enabled`
- AI provider implementations (Stories 4.2-4.3) will fetch this config to construct API requests
- Story 4.1 doesn't need direct access — just awareness of the fields

**PromptTemplate entity** (Story 1.7 — prompt template):
- Fields: `name`, `category`, `templateContent`, `version`, `enabled`
- Service layer will load templates and render with Handlebars (already in service pom.xml: `com.github.jknack:handlebars:4.4.0`)
- AIProvider receives the rendered String, NOT the entity

**FailureType enum** (Story 2.7 — pattern with description + boolean):
```java
public enum FailureType {
    RATE_LIMIT("AI API rate limit exceeded", true),
    NETWORK_ERROR("Network connection failure", true),
    // ...
    private final String description;
    private final boolean retryable;
}
```

### File Structure (MUST follow)

```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/
│   ├── enums/
│   │   ├── IssueSeverity.java              ← NEW: CRITICAL, HIGH, MEDIUM, LOW, INFO
│   │   └── IssueCategory.java             ← NEW: SECURITY, PERFORMANCE, MAINTAINABILITY, etc.
│   ├── dto/review/
│   │   ├── ReviewResult.java              ← NEW: Main review output DTO
│   │   ├── ReviewIssue.java               ← NEW: Individual issue DTO
│   │   └── ReviewMetadata.java            ← NEW: Metadata (model, tokens, timing)
│   └── exception/
│       ├── AIProviderException.java       ← NEW: Base AI exception with statusCode
│       ├── RateLimitException.java        ← NEW: 429 errors
│       ├── AIAuthenticationException.java ← NEW: 401 errors
│       └── AITimeoutException.java        ← NEW: Timeout errors
│
├── ai-code-review-common/src/test/java/com/aicodereview/common/
│   ├── enums/
│   │   ├── IssueSeverityTest.java         ← NEW: Enum validation tests
│   │   └── IssueCategoryTest.java         ← NEW: Enum validation tests
│   ├── dto/review/
│   │   └── ReviewResultTest.java          ← NEW: Factory + serialization tests
│   └── exception/
│       └── AIProviderExceptionTest.java   ← NEW: Exception hierarchy tests
│
├── ai-code-review-integration/src/main/java/com/aicodereview/integration/
│   └── ai/
│       ├── AIProvider.java                ← NEW: Interface
│       └── AIProviderFactory.java         ← NEW: @Component, Factory pattern
│
├── ai-code-review-integration/src/test/java/com/aicodereview/integration/
│   └── ai/
│       └── AIProviderFactoryTest.java     ← NEW: Factory selection tests
│
├── ai-code-review-api/src/main/resources/
│   └── application.yml                    ← MODIFY: Add ai.provider.default
```

### Implementation Patterns

**AIProvider interface:**
```java
public interface AIProvider {
    /**
     * Analyzes code using this AI provider.
     *
     * @param context        the assembled code context (rawDiff, files, statistics)
     * @param renderedPrompt the fully rendered prompt string (template already resolved)
     * @return structured review result with issues and metadata
     */
    ReviewResult analyze(CodeContext context, String renderedPrompt);

    /**
     * Checks if this provider is currently available (configured and reachable).
     */
    boolean isAvailable();

    /**
     * Returns the unique provider identifier (e.g., "openai", "anthropic").
     */
    String getProviderId();

    /**
     * Returns the maximum token limit for this provider's model.
     */
    int getMaxTokens();
}
```

**AIProviderFactory (replicate GitPlatformClientFactory):**
```java
@Component
@Slf4j
public class AIProviderFactory {
    private final Map<String, AIProvider> providerMap;

    @Value("${ai.provider.default:openai}")
    private String defaultProviderId;

    public AIProviderFactory(List<AIProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(AIProvider::getProviderId, Function.identity()));
        log.info("Initialized AI provider factory with {} provider(s): {}",
                providerMap.size(), providerMap.keySet());
    }

    public AIProvider getProvider(String providerId) {
        AIProvider provider = providerMap.get(providerId);
        if (provider == null) {
            throw new UnsupportedPlatformException("No AI provider registered with id: " + providerId);
        }
        return provider;
    }

    public AIProvider getDefaultProvider() {
        return getProvider(defaultProviderId);
    }
}
```

**ReviewResult with static factories:**
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewResult {
    private List<ReviewIssue> issues;
    private ReviewMetadata metadata;
    private boolean success;
    private String errorMessage;

    public static ReviewResult success(List<ReviewIssue> issues, ReviewMetadata metadata) {
        return ReviewResult.builder()
                .issues(issues != null ? issues : List.of())
                .metadata(metadata)
                .success(true)
                .build();
    }

    public static ReviewResult failed(String errorMessage) {
        return ReviewResult.builder()
                .issues(List.of())
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
```

**IssueSeverity with score:**
```java
public enum IssueSeverity {
    CRITICAL(5), HIGH(4), MEDIUM(3), LOW(2), INFO(1);

    private final int score;

    IssueSeverity(int score) { this.score = score; }
    public int getScore() { return score; }
}
```

**IssueCategory with displayName:**
```java
public enum IssueCategory {
    SECURITY("Security"),
    PERFORMANCE("Performance"),
    MAINTAINABILITY("Maintainability"),
    CORRECTNESS("Correctness"),
    STYLE("Code Style"),
    BEST_PRACTICES("Best Practices");

    private final String displayName;

    IssueCategory(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
```

**AIProviderException hierarchy:**
```java
public class AIProviderException extends RuntimeException {
    private final int statusCode;

    public AIProviderException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AIProviderException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() { return statusCode; }
}

public class RateLimitException extends AIProviderException {
    public RateLimitException(String message) { super(429, message); }
}

public class AIAuthenticationException extends AIProviderException {
    public AIAuthenticationException(String message) { super(401, message); }
}

public class AITimeoutException extends AIProviderException {
    public AITimeoutException(String message) { super(408, message); }
    public AITimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### Critical Patterns from Previous Stories (MUST follow)

1. **-parameters compiler flag NOT enabled**: Use `@Value("${ai.provider.default:openai}")` with explicit property name. Use `@PathVariable("id")` with explicit value if adding any controllers.
2. **Lombok DTOs**: Use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` for all DTOs.
3. **Enum test pattern**: Follow `ChangeTypeTest`, `FailureTypeTest` — assert enum size, assert specific values/mappings.
4. **Factory pattern**: Follow `GitPlatformClientFactory` exactly — `List<T>` constructor injection, `Map<K, T>` lookup, log on init.
5. **Exception pattern**: Follow `GitApiException` — `statusCode` field, multiple constructors.
6. **DTO in common module**: All DTOs go in `common/dto/` (not service or integration module).
7. **Enums in common module**: All enums go in `common/enums/`.
8. **No integration tests needed**: Story 4.1 has no external dependencies. Pure unit tests suffice.
9. **Jackson serialization**: Ensure DTOs serialize/deserialize correctly with Jackson. Add `jackson-databind` test dependency to common module if not already present (it was added as test-scope in Story 3.3).

### Previous Story Intelligence (Story 3.3)

**Learnings from Story 3.3:**
- `CodeContext` DTO is the input that AIProvider will consume — it has rawDiff, files, fileContents, statistics, taskMeta
- Maven multi-module build order can cause `NoClassDefFoundError` — run `mvn install` on common module first if adding new classes there
- `jackson-databind` was added as test-scope dependency to common module pom.xml in Story 3.3 — reuse for ReviewResult serialization tests
- Static factory methods (like `ReviewResult.success()` / `ReviewResult.failed()`) are common in this project

**Files created in Story 3.3** (integration points):
- `CodeContext.java` in `common/dto/reviewtask/` — input to `AIProvider.analyze()`
- `ReviewContextAssembler.java` in `service/` — produces CodeContext from ReviewTask
- `V7__add_code_context_to_review_task.sql` — code_context column in review_task table

### Edge Cases to Handle

1. **Empty provider list** → AIProviderFactory should initialize with empty map, throw on any getProvider() call
2. **Duplicate provider IDs** → If two providers have same getProviderId(), Collectors.toMap will throw — log clearly
3. **Null providerId in getProvider()** → Throw UnsupportedPlatformException (not NPE)
4. **ReviewResult with null issues** → success() factory should default to empty list
5. **ReviewIssue with null filePath/line** → Valid (for general issues not tied to specific file/line)
6. **ReviewMetadata with empty degradationEvents** → Default to empty list, not null

### Dependencies

**No new Maven dependencies required:**
- All existing common module dependencies (Lombok, Jackson annotations) are sufficient
- `jackson-databind` already in common module as test-scope (from Story 3.3)
- Integration module already has Spring context and SLF4J

### References

- [Source: epics.md — Epic 4: Story 4.1 AI 提供商抽象层 — full specifications]
- [Source: architecture.md — Decision 3.4: AI Provider Abstraction — Strategy + Factory pattern]
- [Source: architecture.md — Error Handling Hierarchy — AIProviderException tree]
- [Source: architecture.md — Package Naming — com.aicodereview.{module}]
- [Source: GitPlatformClientFactory.java — Factory pattern to replicate]
- [Source: GitPlatformClient.java — Interface pattern to replicate]
- [Source: GitApiException.java — Exception pattern to replicate]
- [Source: CodeContext.java — Input DTO for AIProvider.analyze()]
- [Source: AiModelConfig.java — AI model configuration entity (Story 1.6)]
- [Source: PromptTemplate.java — Prompt template entity (Story 1.7)]
- [Source: FailureType.java — Enum pattern with fields (Story 2.7)]
- [Source: epic-3-retro-2026-02-14.md — Epic 4 architecture decisions: native HttpClient, single-call, 4 stories]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

None — clean implementation with no debugging required.

### Completion Notes List

- ✅ Task 1: Created `IssueSeverity` enum with 5 values (CRITICAL=5, HIGH=4, MEDIUM=3, LOW=2, INFO=1) and `IssueSeverityTest` (4 tests)
- ✅ Task 2: Created `IssueCategory` enum with 6 values and displayNames, `IssueCategoryTest` (4 tests)
- ✅ Task 3: Created AI exception hierarchy: `AIProviderException` (base, statusCode), `RateLimitException` (429), `AIAuthenticationException` (401), `AITimeoutException` (408), `AIProviderExceptionTest` (6 tests)
- ✅ Task 4: Created `ReviewIssue`, `ReviewMetadata` (with `@Builder.Default` for degradationEvents), `ReviewResult` (with success/failed static factories), `ReviewResultTest` (9 tests including JSON serialization round-trip)
- ✅ Task 5: Created `AIProvider` interface with 4 methods: analyze, isAvailable, getProviderId, getMaxTokens
- ✅ Task 6: Created `AIProviderFactory` with constructor injection via `@Value` for defaultProviderId (not field injection), `AIProviderFactoryTest` (6 tests with stub providers)
- ✅ Task 7: Added `ai.provider.default: ${AI_PROVIDER_DEFAULT:openai}` to application.yml
- ✅ Task 8: Full test suite — 241 tests pass across common/integration/repository/service modules. API module has pre-existing integration test failures unrelated to Story 4.1.

### Senior Developer Review (AI)

**Review Date**: 2026-02-14
**Review Outcome**: Approved (after fixes)
**Issues Found**: 1 HIGH + 3 MEDIUM + 1 LOW = 5 total
**All Fixed**: 5/5

**Action Items (all resolved):**
- [x] H1: AIProviderFactory duplicate provider ID — added merge function with clear error message and logging
- [x] M1: Missing duplicate provider ID test — added `shouldThrowForDuplicateProviderIds` test
- [x] M2: ReviewResult.success() null metadata — added `Objects.requireNonNull` validation + test
- [x] M3: ReviewIssue all-fields builder test — added `reviewIssueBuilderShouldPopulateAllFields` test
- [x] L1: AIProviderException three-arg constructor — added `(statusCode, message, cause)` constructor + test

### Change Log

- 2026-02-14: Story 4.1 implemented — AI provider abstraction layer with strategy + factory pattern
- 2026-02-14: Adversarial code review — fixed 5 issues (1H + 3M + 1L), added 4 new tests

### File List

**New files (source):**
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/IssueSeverity.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/IssueCategory.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/AIProviderException.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/RateLimitException.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/AIAuthenticationException.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/AITimeoutException.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewIssue.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewMetadata.java`
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewResult.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/AIProvider.java`
- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/AIProviderFactory.java`

**New files (test):**
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/enums/IssueSeverityTest.java`
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/enums/IssueCategoryTest.java`
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/exception/AIProviderExceptionTest.java`
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/dto/review/ReviewResultTest.java`
- `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/ai/AIProviderFactoryTest.java`

**Modified files:**
- `backend/ai-code-review-api/src/main/resources/application.yml` (added `ai.provider.default` config)
