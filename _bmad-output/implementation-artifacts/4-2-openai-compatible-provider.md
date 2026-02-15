# Story 4.2: OpenAI 兼容提供商（含自定义 Endpoint）

Status: done

> **Merge Note**: This story merges original Story 4.2 (OpenAI) + original Story 4.4 (Custom OpenAPI).
> Both use the same OpenAI Chat Completions API format — only baseUrl differs.

## Story

As a **system**,
I want to **integrate OpenAI-compatible APIs for code review**,
so that **GPT models or any OpenAI-compatible privately deployed models can be used to analyze code**.

## Acceptance Criteria

1. **AC1: OpenAICompatibleProvider class** — Create `OpenAICompatibleProvider` implementing `AIProvider` in integration module (`integration/ai/openai/`):
   - `@Component` with provider ID `"openai"`
   - Uses native `java.net.http.HttpClient` (reuse the existing `gitHttpClient` bean from `GitClientConfig`)
   - Loads configuration from `AiModelConfig` entity (fetched by service layer, passed or injected)

2. **AC2: Configurable baseUrl** — Support both official and custom endpoints:
   - Official OpenAI: `https://api.openai.com` (default when `apiEndpoint` is null/empty)
   - Custom endpoint: Any OpenAI-compatible API address (e.g., Azure OpenAI, private deployments)
   - Base URL sourced from `AiModelConfig.apiEndpoint`

3. **AC3: Chat Completions request construction** — Build HTTP POST request to `{baseUrl}/v1/chat/completions`:
   - Headers: `Authorization: Bearer {apiKey}`, `Content-Type: application/json`
   - Request body JSON:
     ```json
     {
       "model": "{modelName}",
       "max_tokens": {maxTokens},
       "temperature": {temperature},
       "response_format": { "type": "json_object" },
       "messages": [
         { "role": "system", "content": "{renderedPrompt}" },
         { "role": "user", "content": "{CodeContext serialized}" }
       ]
     }
     ```
   - System message: The `renderedPrompt` parameter (six-dimension review instructions, already rendered by service layer)
   - User message: CodeContext content (rawDiff + file list + statistics + taskMeta)

4. **AC4: JSON response parsing** — Parse Chat Completions response:
   - Extract `choices[0].message.content` as the AI response text
   - Parse content as structured `List<ReviewIssue>` (JSON array of issue objects)
   - Extract `usage.prompt_tokens` and `usage.completion_tokens` into `ReviewMetadata`
   - Populate `ReviewMetadata` with providerId, model, token counts, durationMs

5. **AC5: Error handling** — Map HTTP status codes to exception hierarchy:
   - 429 Rate Limit → throw `RateLimitException`
   - 401 Unauthorized → throw `AIAuthenticationException`
   - 408/504 Timeout → throw `AITimeoutException`
   - 500/502/503 Server Error → throw `AIProviderException`
   - `java.net.http.HttpTimeoutException` → wrap as `AITimeoutException`
   - Network/IO errors → wrap as `AIProviderException`

6. **AC6: Retry logic** — Reuse Story 3.2 `executeWithRetry` pattern:
   - 429/5xx: Max 2 retries, exponential backoff (1s, 2s)
   - 401/403/404: No retry (throw immediately)
   - `HttpTimeoutException`: No retry (delegate to upper-layer degradation strategy)
   - `InterruptedException`: Restore interrupt flag and throw

7. **AC7: API call metrics** — Log request/response details:
   - INFO: model name, token usage (prompt + completion), duration in ms
   - WARN: retry attempts with status code and delay
   - ERROR: final failure with status code and error body

8. **AC8: isAvailable() and getMaxTokens()** — Implement interface methods:
   - `isAvailable()`: Return true if apiKey is configured (non-null, non-empty)
   - `getMaxTokens()`: Return value from `AiModelConfig.maxTokens`

9. **AC9: Unit tests** — Comprehensive tests with mocked HttpClient:
   - Test request URL construction (official endpoint + custom endpoint)
   - Test Authorization header construction
   - Test request body JSON structure (model, messages, temperature, response_format)
   - Test successful response parsing (JSON → ReviewResult with issues + metadata)
   - Test each error status code → correct exception type
   - Test retry logic (429 then success, 5xx then success)
   - Test max retries exhausted → exception
   - Test HttpTimeoutException → AITimeoutException (no retry)
   - Test empty/null API key → isAvailable() returns false
   - Test malformed JSON response handling

## Tasks / Subtasks

- [x] Task 1: Create OpenAICompatibleProvider class skeleton (AC: #1, #2, #8)
  - [x] 1.1 Create `OpenAICompatibleProvider` in `integration/ai/openai/` implementing `AIProvider`
  - [x] 1.2 Inject `HttpClient` bean and configuration (apiKey, apiEndpoint, modelName, maxTokens, temperature, timeoutSeconds)
  - [x] 1.3 Implement `getProviderId()` returning `"openai"`
  - [x] 1.4 Implement `isAvailable()` checking apiKey presence
  - [x] 1.5 Implement `getMaxTokens()` from config
  - [x] 1.6 Default baseUrl to `https://api.openai.com` when apiEndpoint is null/empty

- [x] Task 2: Implement request building (AC: #3)
  - [x] 2.1 Build `HttpRequest` with POST method to `{baseUrl}/v1/chat/completions`
  - [x] 2.2 Set headers: `Authorization: Bearer {apiKey}`, `Content-Type: application/json`
  - [x] 2.3 Set request timeout from config (`timeoutSeconds`)
  - [x] 2.4 Build request body JSON with ObjectMapper: model, max_tokens, temperature, response_format, messages array
  - [x] 2.5 Serialize CodeContext fields into user message content string

- [x] Task 3: Implement response parsing (AC: #4)
  - [x] 3.1 Parse response body JSON with ObjectMapper
  - [x] 3.2 Extract `choices[0].message.content`
  - [x] 3.3 Parse AI response content as `List<ReviewIssue>`
  - [x] 3.4 Extract `usage.prompt_tokens` and `usage.completion_tokens`
  - [x] 3.5 Build `ReviewMetadata` (providerId, model, tokens, durationMs)
  - [x] 3.6 Return `ReviewResult.success(issues, metadata)`
  - [x] 3.7 Handle malformed/unparseable AI response gracefully (return `ReviewResult.failed(...)`)

- [x] Task 4: Implement error handling and retry (AC: #5, #6, #7)
  - [x] 4.1 Create `executeWithRetry` method following GitHubApiClient pattern
  - [x] 4.2 Map status codes to AI exception types (429→RateLimitException, 401→AIAuthenticationException, etc.)
  - [x] 4.3 Implement exponential backoff: `delay = (long) Math.pow(2, attempt - 1) * 1000`
  - [x] 4.4 Retry on 429 and 5xx (max 2 retries); throw immediately on 401/403/404
  - [x] 4.5 Catch `HttpTimeoutException` → wrap as `AITimeoutException` (no retry)
  - [x] 4.6 Catch `InterruptedException` → restore interrupt flag, wrap as `AIProviderException`
  - [x] 4.7 Log WARN on retries, INFO on success with metrics, ERROR on final failure

- [x] Task 5: Implement `analyze()` method (AC: #1, #3, #4, #7)
  - [x] 5.1 Record start time for duration measurement
  - [x] 5.2 Build request body from CodeContext + renderedPrompt
  - [x] 5.3 Call `executeWithRetry` to send request
  - [x] 5.4 Parse response and build ReviewResult
  - [x] 5.5 Log metrics (model, tokens, duration)

- [x] Task 6: Write unit tests (AC: #9)
  - [x] 6.1 Test request URL: official endpoint (`https://api.openai.com/v1/chat/completions`)
  - [x] 6.2 Test request URL: custom endpoint (`https://my-proxy.example.com/v1/chat/completions`)
  - [x] 6.3 Test Authorization header format
  - [x] 6.4 Test request body JSON structure
  - [x] 6.5 Test successful response parsing (valid ReviewResult with issues + metadata)
  - [x] 6.6 Test 429 → RateLimitException
  - [x] 6.7 Test 401 → AIAuthenticationException
  - [x] 6.8 Test 500/502/503 → AIProviderException
  - [x] 6.9 Test retry on 429 then success on 2nd attempt
  - [x] 6.10 Test retry on 500 then success on 2nd attempt
  - [x] 6.11 Test max retries exhausted (3x 429) → RateLimitException
  - [x] 6.12 Test HttpTimeoutException → AITimeoutException (no retry)
  - [x] 6.13 Test isAvailable() true/false
  - [x] 6.14 Test malformed JSON response → ReviewResult.failed()

- [x] Task 7: Verify build (AC: all)
  - [x] 7.1 Run `mvn clean test` — all tests pass, zero regressions
  - [x] 7.2 Verify no new warnings or compilation errors

## Dev Notes

### Configuration Injection Pattern
The provider needs access to `AiModelConfig` fields. Two approaches:
- **Option A (Preferred)**: Inject config values via constructor parameters or `@Value` properties so the provider is self-contained. The `analyze()` method receives `CodeContext` and `renderedPrompt` — the caller (orchestrator in Story 4.4) loads the `AiModelConfig` and passes relevant fields or injects them.
- **Option B**: Accept `AiModelConfig` as a method parameter in `analyze()`. However, this changes the `AIProvider` interface signature established in Story 4.1 — **avoid this**.

Since `AIProvider.analyze(CodeContext, String)` is fixed from Story 4.1, configuration must be injected at construction time via `@Value` properties or a config holder.

**Recommended approach**: Use `@Value` for api key, endpoint, model, temperature, maxTokens, timeoutSeconds:
```java
@Value("${ai.provider.openai.api-key:}")
private String apiKey;

@Value("${ai.provider.openai.api-endpoint:https://api.openai.com}")
private String apiEndpoint;

@Value("${ai.provider.openai.model:gpt-4}")
private String model;

// etc.
```

### HTTP Client Pattern (from GitHubApiClient)
Reuse the exact `executeWithRetry` pattern from `GitHubApiClient`:
```java
private static final int MAX_RETRIES = 2;

// Exponential backoff: attempt 1 → 1s, attempt 2 → 2s
long delay = (long) Math.pow(2, attempt - 1) * 1000;
```

Key differences from GitHubApiClient:
- POST request (not GET)
- Request body (JSON payload)
- Different auth header value (same `Authorization: Bearer` format)
- Maps to AI-specific exceptions instead of `GitApiException`

### OpenAI Chat Completions API Format
**Request**:
```json
POST {baseUrl}/v1/chat/completions
{
  "model": "gpt-4",
  "max_tokens": 4000,
  "temperature": 0.3,
  "response_format": { "type": "json_object" },
  "messages": [
    { "role": "system", "content": "You are a code review assistant..." },
    { "role": "user", "content": "Review the following code changes:\n\n<diff>...</diff>" }
  ]
}
```

**Response**:
```json
{
  "choices": [
    {
      "message": {
        "content": "{\"issues\": [{\"severity\": \"HIGH\", ...}]}"
      }
    }
  ],
  "usage": {
    "prompt_tokens": 1500,
    "completion_tokens": 800
  }
}
```

### User Message Content Construction
Serialize `CodeContext` fields into the user message:
- `rawDiff`: The unified diff content
- `files`: List of changed files with metadata
- `statistics`: Lines added/removed counts
- `taskMeta`: PR/branch/author info

Use a simple structured text format (not JSON serialization of the DTO itself).

### Existing Beans to Reuse
- `HttpClient` bean from `GitClientConfig` — named `gitHttpClient`, configured with connect timeout
- `ObjectMapper` — create local instance (same as `GitHubApiClient` pattern) for request/response JSON

### Testing Pattern
Mock `HttpClient.send()` to return crafted `HttpResponse<String>` objects:
```java
@Mock private HttpClient httpClient;
@Mock private HttpResponse<String> httpResponse;

when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
    .thenReturn(httpResponse);
when(httpResponse.statusCode()).thenReturn(200);
when(httpResponse.body()).thenReturn("{...json...}");
```

### Project Structure Notes

- **Source file**: `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/openai/OpenAICompatibleProvider.java`
- **Test file**: `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/ai/openai/OpenAICompatibleProviderTest.java`
- **Config additions**: `backend/ai-code-review-api/src/main/resources/application.yml` — add `ai.provider.openai.*` properties
- No new dependencies needed — `jackson-databind` and `java.net.http` already available

### Key Constraints
- `-parameters` compiler flag NOT enabled: use `@Value("${...}")` with explicit property names
- Do NOT use OpenAI Java SDK — use native `java.net.http.HttpClient` per architecture decision
- The `HttpClient` bean is named `gitHttpClient` — may need to rename or add a qualifier if AI provider needs different connect timeout. Consider reusing as-is since connect timeout (5s) is reasonable for AI APIs too.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.2] — Full acceptance criteria
- [Source: _bmad-output/planning-artifacts/architecture.md#AI Provider Strategy] — Architecture pattern
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitHubApiClient.java] — executeWithRetry pattern
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/config/GitClientConfig.java] — HttpClient bean
- [Source: backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/AiModelConfig.java] — Config entity fields
- [Source: backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/reviewtask/CodeContext.java] — CodeContext DTO
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/AIProvider.java] — Interface contract
- [Source: _bmad-output/implementation-artifacts/4-1-ai-provider-abstraction-layer.md] — Story 4.1 (prerequisite)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Fixed `ChangeType.MODIFIED` → `ChangeType.MODIFY` (enum value name mismatch)
- Fixed retry test mock setup: `body()` only called once (on success), not sequentially

### Completion Notes List

- Implemented `OpenAICompatibleProvider` with full Chat Completions API integration
- Constructor-injected configuration via `@Value` properties (apiKey, apiEndpoint, model, maxTokens, temperature, timeoutSeconds)
- Configurable baseUrl: defaults to `https://api.openai.com`, supports any OpenAI-compatible endpoint
- User message builds structured text from CodeContext (taskMeta, statistics, files, rawDiff, fileContents)
- Response parsing: extracts `choices[0].message.content`, parses as `List<ReviewIssue>`, extracts token usage
- Error handling: 429→RateLimitException, 401→AIAuthenticationException, 403/404→AIProviderException, 408/504→AITimeoutException, HttpTimeoutException→AITimeoutException, IOException→AIProviderException
- Retry logic: max 2 retries with exponential backoff (1s, 2s) for 429/5xx; no retry for 401/403/404/timeout
- Malformed JSON response returns `ReviewResult.failed()` instead of throwing
- 27 unit tests covering all ACs: provider identity, baseUrl config, request construction, response parsing, error handling, retry logic, edge cases
- All tests pass across all modules with 0 regressions

### Change Log

- 2026-02-15: Story 4.2 implementation complete — OpenAI Compatible Provider with 27 tests
- 2026-02-15: Code review fixes applied — 5 issues resolved (1 HIGH, 3 MEDIUM, 1 LOW)

## Senior Developer Review (AI)

**Review Date**: 2026-02-15
**Review Outcome**: Approve (after fixes)

### Action Items

- [x] H1: Added ERROR logging for final API call failures in `executeWithRetry` (AC7 compliance)
- [x] M1: Removed unused import `java.util.stream.Collectors`
- [x] M2: Changed `buildRequestBody` from package-private to `private`
- [x] M3: Strengthened `shouldBuildCorrectRequestBody` test to verify actual JSON content (model, temperature, response_format, messages structure)
- [x] L1: Sorted `fileContents` map keys before iteration for deterministic user message output

### File List

- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/openai/OpenAICompatibleProvider.java` (NEW)
- `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/ai/openai/OpenAICompatibleProviderTest.java` (NEW)
- `backend/ai-code-review-api/src/main/resources/application.yml` (MODIFIED — added `ai.provider.openai.*` properties)
