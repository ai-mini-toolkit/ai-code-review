# Story 4.4: 审查编排与 Prompt 管理与降级策略

Status: done

> **简化说明**: 原 Story 4.5 采用 6 维度并发执行（CompletableFuture + Semaphore）。
> 基于 Epic 3 回顾决策，改为**单次 API 调用覆盖全部六维度**，大幅降低成本和复杂度。
> 本 Story 是 Epic 4 的最后一个 Story，整合所有已实现组件（AIProvider、ReviewContextAssembler、PromptTemplate）。

## Story

As a **system**,
I want to **orchestrate the code review flow, render prompts, and implement a provider fallback strategy**,
so that **code reviews are completed reliably using AI with automatic fallback to backup providers on failure**.

## Acceptance Criteria

1. **AC1: ReviewOrchestrator service** — Create `ReviewOrchestrator` in service module:
   - `ReviewResult review(ReviewTask task)` — main entry point
   - Coordinates: context assembly → prompt rendering → AI provider call → result return
   - Lives in `com.aicodereview.service` (service module)

2. **AC2: Single-call review flow** — Implement the full pipeline:
   1. Call `ReviewContextAssembler.assembleContext(task)` → `CodeContext`
   2. Load the active prompt template (category = `"code-review"`, enabled = true)
   3. Render prompt template with Handlebars (inject CodeContext fields)
   4. Select primary AI provider via `AIProviderFactory`
   5. Call `AIProvider.analyze(codeContext, renderedPrompt)`
   6. Return `ReviewResult`

3. **AC3: Prompt rendering** — Render PromptTemplate with Handlebars:
   - Load `PromptTemplate` from repository (first enabled template in `"code-review"` category)
   - Use existing Handlebars engine (already in service module via `com.github.jknack.handlebars`)
   - Template variables: `{{rawDiff}}`, `{{files}}`, `{{statistics}}`, `{{taskMeta}}`
   - The rendered prompt becomes the `renderedPrompt` parameter passed to `AIProvider.analyze()`
   - If no enabled template found → throw `ResourceNotFoundException`

4. **AC4: Provider fallback/degradation strategy** — Three-level degradation:
   - **Level 0: Primary provider** (from `ai.provider.default` config, e.g., `"openai"`)
     - Provider already handles internal retries (429/5xx → max 2 retries with backoff)
     - If provider throws any `AIProviderException` → fall through to Level 1
   - **Level 1: Fallback provider** (from `ai.provider.fallback` config, e.g., `"anthropic"`)
     - Same call: `fallbackProvider.analyze(codeContext, renderedPrompt)`
     - If fallback also throws `AIProviderException` → fall through to Level 2
   - **Level 2: Complete failure**
     - Return `ReviewResult.failed(errorMessage)` with degradation event chain
   - Record all degradation events in `ReviewMetadata.degradationEvents`

5. **AC5: Fallback provider config** — Add new config property:
   - `ai.provider.fallback: ${AI_PROVIDER_FALLBACK:anthropic}` in application.yml
   - If fallback provider is same as primary → skip Level 1 (no point retrying same provider)
   - If fallback provider is not available (`isAvailable() == false`) → skip Level 1

6. **AC6: Micrometer metrics** — Instrument key operations:
   - `ai.review.duration` (Timer) — total review time including fallback attempts
   - `ai.review.success` (Counter) — successful reviews
   - `ai.review.failure` (Counter) — failed reviews (after all fallbacks exhausted)
   - `ai.review.degradation` (Counter, tag: `from`/`to`) — fallback events
   - `ai.review.provider.used` (Counter, tag: `provider`) — which provider served the request

7. **AC7: Unit tests** — Comprehensive tests with mocked dependencies:
   - Test normal flow (context assembly → render → AI call → success)
   - Test primary provider fails → fallback succeeds
   - Test both providers fail → `ReviewResult.failed()`
   - Test primary succeeds without invoking fallback
   - Test prompt template rendering with Handlebars
   - Test no enabled template → `ResourceNotFoundException`
   - Test fallback same as primary → skipped
   - Test fallback provider unavailable → skipped
   - Test degradation events recorded in metadata
   - Test `AIAuthenticationException` still triggers fallback
   - Test `RateLimitException` (after internal retries exhausted) still triggers fallback

## Tasks / Subtasks

- [x] Task 1: Create ReviewOrchestrator service class (AC: #1, #2)
  - [x] 1.1 Create `ReviewOrchestrator` in service module (`com.aicodereview.service`)
  - [x] 1.2 Inject: `ReviewContextAssembler`, `PromptTemplateRepository`, `AIProviderFactory`, `MeterRegistry`
  - [x] 1.3 Inject fallback provider ID via `@Value("${ai.provider.fallback:anthropic}")`
  - [x] 1.4 Implement `review(ReviewTask task): ReviewResult` main method

- [x] Task 2: Implement prompt rendering (AC: #3)
  - [x] 2.1 Load first enabled `PromptTemplate` with category `"code-review"` from repository
  - [x] 2.2 Render template with Handlebars: `HANDLEBARS.compileInline(template.getTemplateContent()).apply(contextMap)`
  - [x] 2.3 Build context map from CodeContext: `rawDiff`, `files` (serialized), `statistics` (serialized), `taskMeta` (serialized)
  - [x] 2.4 Throw `ResourceNotFoundException` if no enabled template found

- [x] Task 3: Implement provider fallback strategy (AC: #4, #5)
  - [x] 3.1 Try primary provider: `primaryProvider.analyze(codeContext, renderedPrompt)`
  - [x] 3.2 On `AIProviderException`: log degradation event, attempt fallback provider
  - [x] 3.3 Skip fallback if: same ID as primary, or `isAvailable() == false`
  - [x] 3.4 On fallback success: merge degradation events into metadata
  - [x] 3.5 On fallback failure: return `ReviewResult.failed()` with full degradation chain

- [x] Task 4: Add fallback config property (AC: #5)
  - [x] 4.1 Add `ai.provider.fallback` to `application.yml`

- [x] Task 5: Implement Micrometer metrics (AC: #6)
  - [x] 5.1 Inject `MeterRegistry` and create Timer/Counter instances
  - [x] 5.2 Record `ai.review.duration`, `ai.review.success`, `ai.review.failure`
  - [x] 5.3 Record `ai.review.degradation` with tags, `ai.review.provider.used` with tag

- [x] Task 6: Write unit tests (AC: #7)
  - [x] 6.1 Test normal flow: assembler + template + provider → success
  - [x] 6.2 Test primary fails → fallback succeeds
  - [x] 6.3 Test both fail → ReviewResult.failed()
  - [x] 6.4 Test no enabled template → ResourceNotFoundException
  - [x] 6.5 Test fallback skipped when same as primary
  - [x] 6.6 Test fallback skipped when unavailable
  - [x] 6.7 Test degradation events recorded in metadata
  - [x] 6.8 Test prompt rendering with Handlebars variables
  - [x] 6.9 Test metrics recorded (Timer, Counter)

- [x] Task 7: Verify build (AC: all)
  - [x] 7.1 Run `mvn clean test` — all tests pass, zero regressions (464 total: 130 common + 14 repository + 177 integration + 143 service)

## Dev Notes

### Architecture Overview

```
ReviewOrchestrator (service module)
  ├── ReviewContextAssembler → CodeContext
  ├── PromptTemplateRepository → PromptTemplate
  │   └── Handlebars.compileInline() → renderedPrompt
  ├── AIProviderFactory
  │   ├── getDefaultProvider() → primaryProvider (Level 0)
  │   └── getProvider(fallbackId) → fallbackProvider (Level 1)
  └── MeterRegistry → metrics
```

### Module Placement

- `ReviewOrchestrator` goes in **service module** (`com.aicodereview.service`), NOT integration module
- It orchestrates service-level components: `ReviewContextAssembler` (service), `PromptTemplateRepository` (repository), `AIProviderFactory` (integration)
- This follows the existing pattern: service module depends on integration + repository modules

### Provider Fallback Design

```
Level 0: Primary Provider (ai.provider.default)
  ├── Internal retries handled by provider itself (429/5xx → 2 retries with backoff)
  ├── Success → return ReviewResult
  └── AIProviderException → record degradation event → Level 1

Level 1: Fallback Provider (ai.provider.fallback)
  ├── Skip if: same as primary, or !isAvailable()
  ├── Internal retries handled by provider itself
  ├── Success → return ReviewResult (with degradation events)
  └── AIProviderException → Level 2

Level 2: Complete Failure
  └── return ReviewResult.failed() with degradation chain
```

**Key design decision**: Providers already handle their own internal retries (429/5xx). The orchestrator does NOT add additional retry logic — it only manages the **cross-provider fallback**. This avoids double-retry complexity.

### Prompt Rendering Pattern

Reuse existing Handlebars engine from `PromptTemplateServiceImpl`:
```java
private static final Handlebars HANDLEBARS = new Handlebars();

String renderPrompt(PromptTemplate template, CodeContext context) {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("rawDiff", context.getRawDiff());
    contextMap.put("files", objectMapper.writeValueAsString(context.getFiles()));
    contextMap.put("statistics", objectMapper.writeValueAsString(context.getStatistics()));
    contextMap.put("taskMeta", objectMapper.writeValueAsString(context.getTaskMeta()));

    Template compiled = HANDLEBARS.compileInline(template.getTemplateContent());
    return compiled.apply(contextMap);
}
```

**Note**: The template `category` for code review is `"code-review"`. Use `promptTemplateRepository.findByCategoryAndEnabled("code-review", true)` and take the first result.

### Existing Services to Reuse (DO NOT Recreate)

| Component | Module | Already Implemented |
|-----------|--------|-------------------|
| `ReviewContextAssembler` | service | Story 3.3 — `assembleContext(ReviewTask)` |
| `AIProviderFactory` | integration | Story 4.1 — `getDefaultProvider()`, `getProvider(id)` |
| `OpenAICompatibleProvider` | integration | Story 4.2 — `analyze()` with retry logic |
| `AnthropicProvider` | integration | Story 4.3 — `analyze()` with retry logic |
| `PromptTemplateRepository` | repository | Story 1.7 — `findByCategoryAndEnabled()` |
| `PromptTemplateService` | service | Story 1.7 — Handlebars rendering |
| `ReviewResult` | common | Story 4.1 — `success()`, `failed()` |
| `ReviewMetadata` | common | Story 4.1 — `degradationEvents` list |
| `CodeContext` | common | Story 3.3 — rawDiff, files, fileContents, statistics, taskMeta |

### Configuration Reference

Existing properties in `application.yml`:
```yaml
ai:
  provider:
    default: ${AI_PROVIDER_DEFAULT:openai}
    # ADD THIS:
    fallback: ${AI_PROVIDER_FALLBACK:anthropic}
```

### Dependency Injection Pattern

```java
@Service
@Slf4j
public class ReviewOrchestrator {
    private final ReviewContextAssembler contextAssembler;
    private final PromptTemplateRepository promptTemplateRepository;
    private final AIProviderFactory providerFactory;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final String fallbackProviderId;

    public ReviewOrchestrator(
            ReviewContextAssembler contextAssembler,
            PromptTemplateRepository promptTemplateRepository,
            AIProviderFactory providerFactory,
            MeterRegistry meterRegistry,
            @Value("${ai.provider.fallback:anthropic}") String fallbackProviderId) {
        // ...
        this.objectMapper = new ObjectMapper();
    }
}
```

### Micrometer Metrics Pattern

Follow the same pattern as Story 1.9 (System Monitoring):
```java
private final Timer reviewDurationTimer;
private final Counter reviewSuccessCounter;
private final Counter reviewFailureCounter;

// In constructor:
this.reviewDurationTimer = Timer.builder("ai.review.duration")
    .description("Total review duration including fallback")
    .register(meterRegistry);
this.reviewSuccessCounter = Counter.builder("ai.review.success")
    .description("Successful review count")
    .register(meterRegistry);
this.reviewFailureCounter = Counter.builder("ai.review.failure")
    .description("Failed review count")
    .register(meterRegistry);

// Usage:
Timer.Sample sample = Timer.start(meterRegistry);
// ... do review ...
sample.stop(reviewDurationTimer);
```

For tagged counters (degradation, provider used):
```java
Counter.builder("ai.review.degradation")
    .tag("from", primaryId)
    .tag("to", fallbackId)
    .register(meterRegistry)
    .increment();

Counter.builder("ai.review.provider.used")
    .tag("provider", actualProviderId)
    .register(meterRegistry)
    .increment();
```

### Key Constraints

- `-parameters` compiler flag NOT enabled: use `@Value("${...}")` with explicit property names, use `#p0` in SpEL
- `PromptTemplateRepository` is in the repository module — inject directly (service module depends on repository)
- `AIProviderFactory` is in the integration module — inject directly (service module depends on integration)
- Handlebars `4.4.0` already available in service module pom.xml
- `MeterRegistry` is auto-configured by Spring Boot Actuator (already in API module)
- Jackson `ObjectMapper`: create local instance (same pattern as providers)
- Do NOT use `PromptTemplateService.previewTemplate()` — that's the API-facing preview. Call `HANDLEBARS.compileInline()` directly for rendering.

### Testing Pattern

```java
@ExtendWith(MockitoExtension.class)
class ReviewOrchestratorTest {
    @Mock private ReviewContextAssembler contextAssembler;
    @Mock private PromptTemplateRepository promptTemplateRepository;
    @Mock private AIProviderFactory providerFactory;
    @Mock private AIProvider primaryProvider;
    @Mock private AIProvider fallbackProvider;
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private ReviewOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ReviewOrchestrator(
            contextAssembler, promptTemplateRepository, providerFactory,
            meterRegistry, "anthropic");
    }
}
```

**Note**: Use `SimpleMeterRegistry` (from Micrometer) for tests — it's an in-memory registry that doesn't need Prometheus. Import: `io.micrometer.core.instrument.simple.SimpleMeterRegistry`.

### Previous Story Intelligence

**Story 4.2 & 4.3 Code Review Lessons** (apply from start):
1. All methods that could be `private` MUST be `private`
2. Log ERROR before every throw
3. Sort map entries by key for deterministic output
4. Test body content verification, not just existence
5. Verify `times(N)` on mocked calls to ensure no-retry or retry behavior

**Story 3.3 Pattern (ReviewContextAssembler)**:
- `assembleContext(ReviewTask task)` takes a `ReviewTask` entity (NOT DTO)
- Returns `CodeContext` with rawDiff, files, fileContents, statistics, taskMeta
- Handles errors gracefully (returns empty context on failure, never throws)

### Project Structure Notes

- **Source file**: `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewOrchestrator.java`
- **Test file**: `backend/ai-code-review-service/src/test/java/com/aicodereview/service/ReviewOrchestratorTest.java`
- **Config addition**: `backend/ai-code-review-api/src/main/resources/application.yml` — add `ai.provider.fallback`
- Dependencies already available: Handlebars (4.4.0), Micrometer (via spring-boot-starter-actuator), Jackson

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.4] — Full acceptance criteria
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewContextAssembler.java] — Context assembly (Story 3.3)
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/AIProviderFactory.java] — Provider factory (Story 4.1)
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/AIProvider.java] — Provider interface (Story 4.1)
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/PromptTemplateRepository.java] — Template repository (Story 1.7)
- [Source: backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/PromptTemplateServiceImpl.java] — Handlebars rendering pattern
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewResult.java] — Result DTO (Story 4.1)
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/review/ReviewMetadata.java] — Metadata with degradationEvents (Story 4.1)
- [Source: _bmad-output/implementation-artifacts/4-3-anthropic-claude-provider.md] — Story 4.3 lessons

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- ResourceNotFoundException constructor: used single-arg `(String)` instead of non-existent 2-arg `(String, String)`
- Integration module needed `mvn install` before service module could compile (AIProvider/AIProviderFactory classes)

### Completion Notes List

- ReviewOrchestrator created with full 3-level fallback strategy (primary → fallback → failed)
- Prompt rendering uses Handlebars compileInline with CodeContext fields serialized via ObjectMapper
- 5 Micrometer metrics: duration timer, success/failure counters, degradation counter (tagged), provider.used counter (tagged)
- 18 unit tests covering all 7 ACs with @Nested test organization (17 original + 1 from code review fix)
- All 465 tests pass across 4 modules (130 common + 14 repository + 177 integration + 144 service)

### Code Review Fixes Applied

- **H1**: Removed double-counting of `reviewFailureCounter` in `executeWithFallback()` (was also incremented in `review()`)
- **M1**: Changed imprecise `isGreaterThanOrEqualTo(1.0)` assertion to exact `isEqualTo(1.0)` in failure counter test
- **M2**: Refactored `shouldAttemptFallback()` → `resolveFallbackProvider()` to return provider directly, eliminating redundant `getProvider()` call
- **M3**: Changed template rendering error from `AIProviderException` to `IllegalStateException` (semantically correct exception type)
- **M4**: Added test for generic `catch (Exception e)` path when `contextAssembler` throws RuntimeException
- Removed unused `ReviewMetadata` import

### File List

| File | Action | Description |
|------|--------|-------------|
| `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewOrchestrator.java` | NEW | Core orchestrator: context assembly → prompt rendering → AI call → fallback |
| `backend/ai-code-review-service/src/test/java/com/aicodereview/service/ReviewOrchestratorTest.java` | NEW | 18 unit tests with mocked dependencies |
| `backend/ai-code-review-service/pom.xml` | MODIFIED | Added micrometer-core dependency |
| `backend/ai-code-review-api/src/main/resources/application.yml` | MODIFIED | Added ai.provider.fallback config property |
