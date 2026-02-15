# Story 4.3: Anthropic Claude 提供商

Status: done

> **Pattern Note**: This story follows the exact same structure as Story 4.2 (OpenAI Compatible Provider).
> Key differences: different API endpoint, auth header format, system message placement, and response structure.

## Story

As a **system**,
I want to **integrate Anthropic Claude API for code review**,
so that **Claude models can be used to analyze code alongside OpenAI-compatible providers**.

## Acceptance Criteria

1. **AC1: AnthropicProvider class** — Create `AnthropicProvider` implementing `AIProvider` in integration module (`integration/ai/anthropic/`):
   - `@Component` with provider ID `"anthropic"`
   - Uses native `java.net.http.HttpClient` (reuse the existing `gitHttpClient` bean from `GitClientConfig`)
   - Loads configuration via `@Value` properties (same pattern as `OpenAICompatibleProvider`)

2. **AC2: Configurable baseUrl** — Support both official and custom endpoints:
   - Official Anthropic: `https://api.anthropic.com` (default when `apiEndpoint` is null/empty)
   - Custom endpoint: Any Anthropic-compatible API address
   - Base URL sourced from `@Value("${ai.provider.anthropic.api-endpoint:}")`

3. **AC3: Messages API request construction** — Build HTTP POST request to `{baseUrl}/v1/messages`:
   - Headers: `x-api-key: {apiKey}`, `anthropic-version: 2023-06-01`, `Content-Type: application/json`
   - **CRITICAL**: Auth header is `x-api-key` (NOT `Authorization: Bearer` like OpenAI)
   - **CRITICAL**: `anthropic-version` header is required
   - Request body JSON:
     ```json
     {
       "model": "{modelName}",
       "max_tokens": {maxTokens},
       "temperature": {temperature},
       "system": "{renderedPrompt}",
       "messages": [
         { "role": "user", "content": "{CodeContext serialized}" }
       ]
     }
     ```
   - **CRITICAL**: `system` is a top-level string parameter (NOT a message in the `messages` array like OpenAI)
   - User message: CodeContext content (reuse same `buildUserMessage()` logic as OpenAICompatibleProvider)

4. **AC4: JSON response parsing** — Parse Messages API response:
   - Extract `content[0].text` as the AI response text (NOT `choices[0].message.content` like OpenAI)
   - Parse content as structured `List<ReviewIssue>` (JSON array of issue objects)
   - Extract `usage.input_tokens` and `usage.output_tokens` into `ReviewMetadata`
   - **CRITICAL**: Field names differ: `input_tokens`/`output_tokens` (NOT `prompt_tokens`/`completion_tokens`)
   - Map `input_tokens` → `ReviewMetadata.promptTokens`, `output_tokens` → `ReviewMetadata.completionTokens`
   - Populate `ReviewMetadata` with providerId="anthropic", model, token counts, durationMs

5. **AC5: Error handling** — Map HTTP status codes to exception hierarchy:
   - 429 Rate Limit → throw `RateLimitException`
   - 401 Authentication Error → throw `AIAuthenticationException`
   - 403 Permission Error → throw `AIProviderException`
   - 404 Not Found → throw `AIProviderException`
   - 408/504 Timeout → throw `AITimeoutException`
   - 500 API Error → throw `AIProviderException`
   - 529 Overloaded → throw `AIProviderException` (Anthropic-specific, treat as retryable like 5xx)
   - `java.net.http.HttpTimeoutException` → wrap as `AITimeoutException`
   - Network/IO errors → wrap as `AIProviderException`

6. **AC6: Retry logic** — Reuse same pattern as OpenAICompatibleProvider:
   - 429/5xx/529: Max 2 retries, exponential backoff (1s, 2s)
   - 401/403/404: No retry (throw immediately)
   - `HttpTimeoutException`: No retry (delegate to upper-layer degradation strategy)
   - `InterruptedException`: Restore interrupt flag and throw

7. **AC7: API call metrics** — Log request/response details:
   - INFO: model name, token usage (input + output), duration in ms
   - WARN: retry attempts with status code and delay
   - ERROR: final failure with status code and error body (log before throwing)

8. **AC8: isAvailable() and getMaxTokens()** — Implement interface methods:
   - `isAvailable()`: Return true if apiKey is configured (non-null, non-empty)
   - `getMaxTokens()`: Return value from config

9. **AC9: Unit tests** — Comprehensive tests with mocked HttpClient:
   - Test request URL construction (official endpoint + custom endpoint)
   - Test `x-api-key` header (NOT `Authorization: Bearer`)
   - Test `anthropic-version: 2023-06-01` header presence
   - Test request body JSON structure (model, max_tokens, temperature, system as top-level string, messages array)
   - Test successful response parsing (`content[0].text` → ReviewResult with issues + metadata)
   - Test `usage.input_tokens`/`usage.output_tokens` mapping to metadata
   - Test each error status code → correct exception type (including 529)
   - Test retry logic (429 then success, 5xx then success, 529 then success)
   - Test max retries exhausted → exception
   - Test HttpTimeoutException → AITimeoutException (no retry)
   - Test empty/null API key → isAvailable() returns false
   - Test malformed JSON response handling → ReviewResult.failed()

## Tasks / Subtasks

- [x] Task 1: Create AnthropicProvider class skeleton (AC: #1, #2, #8)
  - [x] 1.1 Create `AnthropicProvider` in `integration/ai/anthropic/` implementing `AIProvider`
  - [x] 1.2 Inject `HttpClient` bean and configuration via `@Value` properties (apiKey, apiEndpoint, model, maxTokens, temperature, timeoutSeconds, anthropicVersion)
  - [x] 1.3 Implement `getProviderId()` returning `"anthropic"`
  - [x] 1.4 Implement `isAvailable()` checking apiKey presence
  - [x] 1.5 Implement `getMaxTokens()` from config
  - [x] 1.6 Default baseUrl to `https://api.anthropic.com` when apiEndpoint is null/empty (reuse `resolveBaseUrl` pattern)

- [x] Task 2: Implement request building (AC: #3)
  - [x] 2.1 Build `HttpRequest` with POST method to `{baseUrl}/v1/messages`
  - [x] 2.2 Set headers: `x-api-key: {apiKey}`, `anthropic-version: {version}`, `Content-Type: application/json`
  - [x] 2.3 Set request timeout from config (`timeoutSeconds`)
  - [x] 2.4 Build request body JSON with ObjectMapper: model, max_tokens, temperature, system (top-level string), messages array
  - [x] 2.5 Reuse same `buildUserMessage(CodeContext)` logic from OpenAICompatibleProvider (serialize taskMeta, statistics, files, rawDiff, fileContents)

- [x] Task 3: Implement response parsing (AC: #4)
  - [x] 3.1 Parse response body JSON with ObjectMapper
  - [x] 3.2 Extract `content[0].text` (NOT `choices[0].message.content`)
  - [x] 3.3 Parse AI response content as `List<ReviewIssue>`
  - [x] 3.4 Extract `usage.input_tokens` and `usage.output_tokens`
  - [x] 3.5 Build `ReviewMetadata` (providerId="anthropic", model, inputTokens→promptTokens, outputTokens→completionTokens, durationMs)
  - [x] 3.6 Return `ReviewResult.success(issues, metadata)`
  - [x] 3.7 Handle malformed/unparseable AI response gracefully (return `ReviewResult.failed(...)`)

- [x] Task 4: Implement error handling and retry (AC: #5, #6, #7)
  - [x] 4.1 Create `executeWithRetry` method following OpenAICompatibleProvider pattern
  - [x] 4.2 Map status codes to AI exception types (429→RateLimitException, 401→AIAuthenticationException, 529→AIProviderException retryable, etc.)
  - [x] 4.3 Implement exponential backoff: `delay = (long) Math.pow(2, attempt - 1) * 1000`
  - [x] 4.4 Retry on 429, 5xx, and 529 (max 2 retries); throw immediately on 401/403/404
  - [x] 4.5 Catch `HttpTimeoutException` → wrap as `AITimeoutException` (no retry)
  - [x] 4.6 Catch `InterruptedException` → restore interrupt flag, wrap as `AIProviderException`
  - [x] 4.7 Log ERROR before each throw, WARN on retries, INFO on success with metrics

- [x] Task 5: Implement `analyze()` method (AC: #1, #3, #4, #7)
  - [x] 5.1 Record start time for duration measurement
  - [x] 5.2 Build request body from CodeContext + renderedPrompt
  - [x] 5.3 Call `executeWithRetry` to send request
  - [x] 5.4 Parse response and build ReviewResult
  - [x] 5.5 Log metrics (model, tokens, duration)

- [x] Task 6: Add application.yml configuration (AC: #1, #2)
  - [x] 6.1 Add `ai.provider.anthropic.*` properties to `application.yml`

- [x] Task 7: Write unit tests (AC: #9)
  - [x] 7.1 Test request URL: official endpoint (`https://api.anthropic.com/v1/messages`)
  - [x] 7.2 Test request URL: custom endpoint (`https://my-proxy.example.com/v1/messages`)
  - [x] 7.3 Test `x-api-key` header format (NOT `Authorization: Bearer`)
  - [x] 7.4 Test `anthropic-version` header present with value `2023-06-01`
  - [x] 7.5 Test request body JSON structure (model, max_tokens, temperature, system as top-level string, messages with user role)
  - [x] 7.6 Test successful response parsing (`content[0].text` → valid ReviewResult with issues + metadata)
  - [x] 7.7 Test `usage.input_tokens`/`usage.output_tokens` correctly mapped to ReviewMetadata
  - [x] 7.8 Test 429 → RateLimitException
  - [x] 7.9 Test 401 → AIAuthenticationException
  - [x] 7.10 Test 500 → AIProviderException
  - [x] 7.11 Test 529 → AIProviderException (Anthropic overloaded)
  - [x] 7.12 Test retry on 429 then success on 2nd attempt
  - [x] 7.13 Test retry on 529 then success on 2nd attempt
  - [x] 7.14 Test max retries exhausted (3x 429) → RateLimitException
  - [x] 7.15 Test HttpTimeoutException → AITimeoutException (no retry)
  - [x] 7.16 Test isAvailable() true/false/null
  - [x] 7.17 Test malformed JSON response → ReviewResult.failed()
  - [x] 7.18 Test getProviderId() returns "anthropic"
  - [x] 7.19 Test getMaxTokens() returns configured value

- [x] Task 8: Verify build (AC: all)
  - [x] 8.1 Run `mvn clean test` — all tests pass, zero regressions
  - [x] 8.2 Verify no new warnings or compilation errors

## Dev Notes

### Critical Differences from OpenAI Provider (MUST follow)

| Aspect | OpenAI (`OpenAICompatibleProvider`) | Anthropic (`AnthropicProvider`) |
|--------|-------------------------------------|--------------------------------|
| Provider ID | `"openai"` | `"anthropic"` |
| Default base URL | `https://api.openai.com` | `https://api.anthropic.com` |
| API path | `/v1/chat/completions` | `/v1/messages` |
| Auth header | `Authorization: Bearer {key}` | `x-api-key: {key}` |
| Version header | (none) | `anthropic-version: 2023-06-01` |
| System message | In `messages` array as `{"role":"system",...}` | Top-level `"system"` string field |
| Response content | `choices[0].message.content` | `content[0].text` |
| Token fields | `usage.prompt_tokens` / `usage.completion_tokens` | `usage.input_tokens` / `usage.output_tokens` |
| Extra retryable code | (none) | 529 (overloaded) |
| JSON mode | `"response_format": {"type": "json_object"}` | Not available; instruct via system prompt |

### Configuration Injection Pattern

Same `@Value` injection pattern as `OpenAICompatibleProvider`:
```java
@Value("${ai.provider.anthropic.api-key:}")
private String apiKey;

@Value("${ai.provider.anthropic.api-endpoint:}")
private String apiEndpoint;

@Value("${ai.provider.anthropic.model:claude-sonnet-4-5-20250929}")
private String model;

@Value("${ai.provider.anthropic.max-tokens:4000}")
private int maxTokens;

@Value("${ai.provider.anthropic.temperature:0.3}")
private double temperature;

@Value("${ai.provider.anthropic.timeout-seconds:60}")
private int timeoutSeconds;

@Value("${ai.provider.anthropic.api-version:2023-06-01}")
private String anthropicVersion;
```

Note: Anthropic timeout is 60s (higher than OpenAI 30s) because Claude models tend to have longer inference times.

### Anthropic Messages API Format

**Request**:
```json
POST {baseUrl}/v1/messages
Headers:
  x-api-key: {apiKey}
  anthropic-version: 2023-06-01
  Content-Type: application/json

{
  "model": "claude-sonnet-4-5-20250929",
  "max_tokens": 4000,
  "temperature": 0.3,
  "system": "You are a code review assistant. Respond in JSON format...",
  "messages": [
    { "role": "user", "content": "Review the following code changes:\n\n..." }
  ]
}
```

**Successful Response** (HTTP 200):
```json
{
  "id": "msg_01XFDUDYJgAACzvnptvVoYEL",
  "type": "message",
  "role": "assistant",
  "content": [
    {
      "type": "text",
      "text": "{\"issues\": [{\"severity\": \"HIGH\", ...}]}"
    }
  ],
  "model": "claude-sonnet-4-5-20250929",
  "stop_reason": "end_turn",
  "stop_sequence": null,
  "usage": {
    "input_tokens": 1500,
    "output_tokens": 800
  }
}
```

**Error Response**:
```json
{
  "type": "error",
  "error": {
    "type": "rate_limit_error",
    "message": "Rate limit exceeded"
  }
}
```

### Error Status Codes (Anthropic-specific)

| HTTP Status | Anthropic Type | Exception to Throw | Retryable? |
|------------|---------------|-------------------|------------|
| 401 | `authentication_error` | `AIAuthenticationException` | No |
| 403 | `permission_error` | `AIProviderException(403)` | No |
| 404 | `not_found_error` | `AIProviderException(404)` | No |
| 429 | `rate_limit_error` | `RateLimitException` | Yes |
| 500 | `api_error` | `AIProviderException(500)` | Yes |
| 529 | `overloaded_error` | `AIProviderException(529)` | Yes |
| 408/504 | (timeout) | `AITimeoutException` | No |

### JSON Mode Workaround

Anthropic does NOT have `response_format: {type: "json_object"}` like OpenAI. Instead:
- The system prompt (`renderedPrompt`) must instruct Claude to respond in JSON format
- This is already handled by the service layer — the `renderedPrompt` parameter includes JSON formatting instructions
- Do NOT add any additional JSON formatting instructions in the provider itself

### User Message Construction

Reuse the **exact same** `buildUserMessage(CodeContext)` logic from `OpenAICompatibleProvider`:
- taskMeta section (PR title, description, author, branch)
- statistics section (files changed, lines added/deleted)
- changed files list (path, changeType, language)
- rawDiff in code block
- fileContents sorted by key (deterministic order)

This can be extracted to a shared utility or copy-pasted. Given only two providers exist, copy-paste is acceptable (DRY can be refactored later if a 3rd provider is added).

### Existing Beans to Reuse

- `HttpClient` bean from `GitClientConfig` — named `gitHttpClient`, configured with connect timeout 5s
- `ObjectMapper` — create local instance (same pattern as `OpenAICompatibleProvider` and `GitHubApiClient`)

### Testing Pattern

Same mock pattern as `OpenAICompatibleProvider`:
```java
@Mock private HttpClient httpClient;
@Mock private HttpResponse<String> httpResponse;

when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
    .thenReturn(httpResponse);
when(httpResponse.statusCode()).thenReturn(200);
when(httpResponse.body()).thenReturn("{...anthropic response json...}");
```

Key test differences from OpenAI:
- Verify `x-api-key` header (not `Authorization`)
- Verify `anthropic-version` header present
- Response JSON uses `content[0].text` (not `choices[0].message.content`)
- Token usage fields: `input_tokens`/`output_tokens` (not `prompt_tokens`/`completion_tokens`)
- Test 529 status code (Anthropic-specific overloaded error)

### Project Structure Notes

- **Source file**: `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/anthropic/AnthropicProvider.java`
- **Test file**: `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/ai/anthropic/AnthropicProviderTest.java`
- **Config additions**: `backend/ai-code-review-api/src/main/resources/application.yml` — add `ai.provider.anthropic.*` properties
- No new dependencies needed — `jackson-databind` and `java.net.http` already available in integration module

### Key Constraints

- `-parameters` compiler flag NOT enabled: use `@Value("${...}")` with explicit property names
- Do NOT use Anthropic Java SDK — use native `java.net.http.HttpClient` per architecture decision
- The `HttpClient` bean is named `gitHttpClient` — reuse as-is (connect timeout 5s is reasonable)
- `buildRequestBody` MUST be `private` (not package-private — learned from Story 4.2 code review)
- Sort `fileContents` map keys before iteration for deterministic output (learned from Story 4.2 code review)
- Log ERROR before every throw in `executeWithRetry` (learned from Story 4.2 code review)

### Previous Story Intelligence (Story 4.2)

Code review found 5 issues in Story 4.2 — ensure these are avoided from the start:
1. **H1 (ERROR logging)**: Always log ERROR with status code and body BEFORE throwing exceptions in `executeWithRetry`
2. **M1 (Unused imports)**: Don't import `java.util.stream.Collectors` — use `Map.Entry.comparingByKey()` instead
3. **M2 (Visibility)**: `buildRequestBody` must be `private`, not package-private
4. **M3 (Test quality)**: Request body test must verify actual JSON content, not just that body publisher exists
5. **L1 (Determinism)**: Sort `fileContents` map entries by key before iteration

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.3] — Full acceptance criteria
- [Source: _bmad-output/planning-artifacts/architecture.md#AI Provider Strategy] — Architecture pattern
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/openai/OpenAICompatibleProvider.java] — Reference implementation (Story 4.2)
- [Source: backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/ai/openai/OpenAICompatibleProviderTest.java] — Reference test patterns
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/config/GitClientConfig.java] — HttpClient bean
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/AIProvider.java] — Interface contract
- [Source: backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/AIProviderFactory.java] — Factory (auto-discovers new @Component)
- [Source: _bmad-output/implementation-artifacts/4-2-openai-compatible-provider.md] — Story 4.2 (reference pattern + code review lessons)
- [Source: https://platform.claude.com/docs/en/api/messages] — Anthropic Messages API reference
- [Source: https://platform.claude.com/docs/en/api/errors] — Anthropic error codes

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Fixed `IssueCategory.BUG` → `IssueCategory.CORRECTNESS` (enum value name mismatch)
- Fixed `FileInfo.language` type: `String` → `Language.JAVA` (Language is an enum, not String)
- Fixed `FileInfo.builder()`: removed non-existent `linesAdded`/`linesDeleted` fields
- Fixed retry test `UnnecessaryStubbing`: removed `errorResponse.body()` stubs (retry path doesn't call `body()`)

### Completion Notes List

- Implemented `AnthropicProvider` with full Messages API integration (`/v1/messages`)
- Constructor-injected configuration via `@Value` properties (apiKey, apiEndpoint, model, maxTokens, temperature, timeoutSeconds, anthropicVersion)
- Configurable baseUrl: defaults to `https://api.anthropic.com`, supports any Anthropic-compatible endpoint
- Auth header: `x-api-key` (NOT `Authorization: Bearer`) + `anthropic-version: 2023-06-01`
- System prompt as top-level `"system"` string field (NOT in messages array)
- Response parsing: `content[0].text` (NOT `choices[0].message.content`), `usage.input_tokens`/`usage.output_tokens`
- User message builds structured text from CodeContext (same pattern as OpenAICompatibleProvider)
- Error handling: 429→RateLimitException, 401→AIAuthenticationException, 403/404→AIProviderException, 408/504→AITimeoutException, 529→AIProviderException(retryable)
- Retry logic: max 2 retries with exponential backoff (1s, 2s) for 429/5xx/529; no retry for 401/403/404/timeout
- All Story 4.2 code review lessons applied from start (private methods, sorted fileContents, ERROR logging before throws)
- 35 unit tests covering all ACs (28 original + 7 from code review fixes)
- Added `ai.provider.anthropic.*` properties to application.yml
- All 447 tests pass across 4 modules (common + repository + integration + service), 0 regressions

### Code Review Fixes Applied

- **H1**: Added tests for 408 and 504 HTTP timeout status codes (`shouldThrowTimeoutExceptionOn408`, `shouldThrowTimeoutExceptionOn504`)
- **M1**: Added test for HTTP 404 status code no-retry verification (`shouldNotRetryOn404`)
- **M2**: Added `verify(httpClient, times(1)).send(any(), any())` to IOException test
- **M3**: Added `anthropicVersion` validation to `isAvailable()` + 2 new tests (`shouldNotBeAvailableWithEmptyVersion`, `shouldNotBeAvailableWithNullVersion`)
- **L1**: Accepted — `buildUserMessage` duplication is by-design (same as OpenAI, future extraction per Story 4.4)

### Change Log

- 2026-02-15: Story 4.3 implementation complete — Anthropic Claude Provider with 28 tests
- 2026-02-15: Code review fixes applied — 7 new tests, anthropicVersion validation in isAvailable()

### File List

- `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/ai/anthropic/AnthropicProvider.java` (NEW)
- `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/ai/anthropic/AnthropicProviderTest.java` (NEW)
- `backend/ai-code-review-api/src/main/resources/application.yml` (MODIFIED — added `ai.provider.anthropic.*` properties)
