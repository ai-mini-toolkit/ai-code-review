# Story 3.2: Git 平台 API 客户端

Status: done

## Story

As a **system**,
I want to **retrieve complete file content and Diff content through Git platform APIs (GitHub, GitLab)**,
so that **I can provide complete code context for AI review**.

**Design Philosophy**: Each Git platform has different API conventions. Use a common interface with platform-specific implementations and a Factory for selection. AWS CodeCommit remains a stub (consistent with Story 2.3 webhook strategy).

## Acceptance Criteria

1. **AC1: GitPlatformClient Interface** - Create `GitPlatformClient` in integration module:
   - `getFileContent(repoUrl, commitHash, filePath): String`
   - `getDiff(repoUrl, commitHash): String` (commit diff)
   - `getDiff(repoUrl, baseBranch, headBranch): String` (PR/MR comparison diff)
   - `getPlatform(): GitPlatform`

2. **AC2: GitPlatform Enum** - Create `GitPlatform` enum in common module:
   - Values: `GITHUB`, `GITLAB`, `AWS_CODECOMMIT`
   - `fromRepoUrl(String repoUrl)` static method to detect platform from URL

3. **AC3: GitHubApiClient Implementation**:
   - File content: `GET /repos/{owner}/{repo}/contents/{path}?ref={sha}`, decode Base64
   - Commit diff: `GET /repos/{owner}/{repo}/commits/{sha}`, Accept: `application/vnd.github.diff`
   - PR diff: `GET /repos/{owner}/{repo}/compare/{base}...{head}`, Accept: `application/vnd.github.diff`
   - Authentication: `Authorization: Bearer {token}` header

4. **AC4: GitLabApiClient Implementation**:
   - File content: `GET /api/v4/projects/{id}/repository/files/{path}/raw?ref={sha}` (URL-encode path with `%2F`)
   - Commit diff: `GET /api/v4/projects/{id}/repository/commits/{sha}/diff` — returns JSON array, must be assembled into unified diff format (see Implementation Patterns)
   - MR diff: `GET /api/v4/projects/{id}/repository/compare?from={base}&to={head}` — returns JSON with `diffs` array, same assembly needed
   - `{id}` = URL-encoded project path (e.g., `namespace/project` → `namespace%2Fproject`)
   - Authentication: `PRIVATE-TOKEN: {token}` header

5. **AC5: AWSCodeCommitClient Stub** - All methods throw `UnsupportedOperationException("AWS CodeCommit API client not yet implemented")` (use Java standard `UnsupportedOperationException`, NOT `UnsupportedPlatformException` which is for platform detection failures)

6. **AC6: GitPlatformClientFactory** - Factory pattern to select client by `GitPlatform`:
   - Auto-discovers `@Component` implementations via constructor injection
   - `getClient(GitPlatform): GitPlatformClient`
   - `getClient(String repoUrl): GitPlatformClient` (convenience: detect platform from URL)

7. **AC7: API Token Configuration** - Global tokens in `application.yml`:
   - `git.platform.github.token` (env: `GIT_GITHUB_TOKEN`)
   - `git.platform.gitlab.token` (env: `GIT_GITLAB_TOKEN`)
   - `git.platform.gitlab.base-url` (env: `GIT_GITLAB_BASE_URL`, default: `https://gitlab.com`)

8. **AC8: HTTP Configuration** - Connection timeout 5s, read timeout 10s

9. **AC9: Transient Failure Retry** - Retry on HTTP 429/5xx:
   - Max 2 retries, exponential backoff (1s, 2s)
   - Non-retryable: 400, 401, 403, 404
   - Network timeouts (`HttpTimeoutException`) → wrap as `GitApiException`, do NOT auto-retry

10. **AC10: Unit Tests** - Comprehensive tests:
    - Test API URL construction and headers for each platform
    - Test Base64 decoding (GitHub file content)
    - Test authentication headers set correctly
    - Test error handling (404 not found, 403 forbidden, 500 server error)
    - Test retry on 429/5xx
    - Test Factory returns correct client per platform
    - Test `GitPlatform.fromRepoUrl()` detection
    - Test AWS stub throws UnsupportedOperationException

## Tasks / Subtasks

- [x] Task 1: Create GitPlatform enum in common module (AC: #2)
  - [x] 1.1 Create `GitPlatform.java` in `common/enums/` with values: GITHUB, GITLAB, AWS_CODECOMMIT
  - [x] 1.2 Add `fromRepoUrl(String)` static method to detect platform from URL patterns
  - [x] 1.3 Add unit test `GitPlatformTest` for enum count, URL detection, edge cases

- [x] Task 2: Create GitPlatformClient interface in integration module (AC: #1)
  - [x] 2.1 Create `GitPlatformClient.java` interface in `integration/git/`
  - [x] 2.2 Define 4 methods: getFileContent, getDiff (commit), getDiff (branch comparison), getPlatform

- [x] Task 3: Create shared HttpClient configuration (AC: #8)
  - [x] 3.1 Create `GitClientConfig.java` in `integration/config/` with `@Bean HttpClient`
  - [x] 3.2 Configure connection timeout 5s via `@Value` property

- [x] Task 4: Implement GitHubApiClient (AC: #3, #7, #9)
  - [x] 4.1 Create `GitHubApiClient.java` as `@Component` in `integration/git/`
  - [x] 4.2 Implement `parseOwnerRepo(repoUrl)` helper to extract `{owner}/{repo}` from GitHub URL
  - [x] 4.3 Implement `getFileContent` — GET contents API, Base64 decode response JSON `.content` field
  - [x] 4.4 Implement `getDiff(commitHash)` — GET commits API with Accept: diff
  - [x] 4.5 Implement `getDiff(baseBranch, headBranch)` — GET compare API with Accept: diff
  - [x] 4.6 Add Bearer token auth header, retry logic for 429/5xx

- [x] Task 5: Implement GitLabApiClient (AC: #4, #7, #9)
  - [x] 5.1 Create `GitLabApiClient.java` as `@Component` in `integration/git/`
  - [x] 5.2 Implement `parseProjectId(repoUrl)` helper to extract URL-encoded `namespace/project` from GitLab URL
  - [x] 5.3 Implement `getFileContent` — GET files raw API (URL-encode filePath)
  - [x] 5.4 Implement `getDiff(commitHash)` — GET commits diff API, assemble unified diff from response JSON
  - [x] 5.5 Implement `getDiff(baseBranch, headBranch)` — GET repository compare API
  - [x] 5.6 Add PRIVATE-TOKEN auth header, retry logic for 429/5xx

- [x] Task 6: Create AWSCodeCommitClient stub (AC: #5)
  - [x] 6.1 Create `AWSCodeCommitClient.java` as `@Component` in `integration/git/`
  - [x] 6.2 All methods throw `UnsupportedOperationException` with descriptive message

- [x] Task 7: Create GitPlatformClientFactory (AC: #6)
  - [x] 7.1 Create `GitPlatformClientFactory.java` as `@Component` in `integration/git/`
  - [x] 7.2 Auto-discover clients via `List<GitPlatformClient>` constructor injection
  - [x] 7.3 Implement `getClient(GitPlatform)` and `getClient(String repoUrl)` methods

- [x] Task 8: Add API token configuration to application.yml (AC: #7)
  - [x] 8.1 Add `git.platform.github.token`, `git.platform.gitlab.token`, `git.platform.gitlab.base-url` properties
  - [x] 8.2 Token injection via `@Value` in client constructors, env var defaults for security

- [x] Task 9: Write comprehensive unit tests (AC: #10)
  - [x] 9.1 Create `GitPlatformTest.java` for enum + URL detection (10 tests)
  - [x] 9.2 Create `GitHubApiClientTest.java` — mock HttpClient, test URL construction, headers, Base64 decode, error handling, retry (17 tests)
  - [x] 9.3 Create `GitLabApiClientTest.java` — mock HttpClient, test URL construction, headers, error handling, retry (16 tests)
  - [x] 9.4 Create `AWSCodeCommitClientTest.java` — verify all methods throw UnsupportedOperationException (4 tests)
  - [x] 9.5 Create `GitPlatformClientFactoryTest.java` — test client selection, unknown platform handling (8 tests)

- [x] Task 10: Verify build and run full test suite (AC: all)
  - [x] 10.1 Run `mvn compile test` across common, integration, service modules
  - [x] 10.2 Verified no regressions: 97 common + 106 integration + 95 service = 298 tests, 0 failures
  - [x] 10.3 All 55 new tests pass

## Dev Notes

### Architecture Decisions

**Module Placement**:
- `GitPlatform` enum → `common/enums/` (shared across modules, used by Factory and future stories)
- `GitPlatformClient` interface + implementations → `integration/git/` (external API integration)
- `GitPlatformClientFactory` → `integration/git/` (client selection logic)
- `GitClientConfig` → `integration/config/` (HttpClient bean)
- [Source: architecture.md — integration module structure shows `git/` directory for Git operations]
- [Source: epics.md — Story 3.2 — "GitPlatformClientFactory" using Factory Pattern]

**HTTP Client**: Java 11+ `java.net.http.HttpClient` (NOT RestTemplate)
- Established pattern in `AiModelConfigServiceImpl.java:43-45`
- HttpClient injected as `@Bean` for testability
- [Source: AiModelConfigServiceImpl — `HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()`]

**API Token Strategy**: Global `application.yml` tokens for initial implementation
- Per-project `accessToken` column in Project entity deferred to future story
- No Flyway migration needed in this story
- [Source: epics.md — "Or use global configuration in application.yml (initial approach, choose one in Story)"]

**AWS CodeCommit Stub**: Consistent with Story 2.3 webhook verification strategy
- `AWSCodeCommitWebhookVerifier.java` already exists as a full implementation
- API client is a stub because full AWS SDK integration requires `software.amazon.awssdk:codecommit` dependency and IAM credentials — deferred to later
- [Source: epics.md — "AWSCodeCommitClient as a Stub (marked with TODO)"]

### Existing Code to Reuse / Integrate With

**Webhook Verifier Pattern** (Chain of Responsibility → replicate for Factory):
```java
// From WebhookVerificationChain.java — same pattern for GitPlatformClientFactory
@Component
public class WebhookVerificationChain {
    private final Map<String, WebhookVerifier> verifierMap;

    public WebhookVerificationChain(List<WebhookVerifier> verifiers) {
        this.verifierMap = verifiers.stream()
            .collect(Collectors.toMap(WebhookVerifier::getPlatform, v -> v));
    }
}
```

**HttpClient Pattern** (from AiModelConfigServiceImpl):
```java
private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .header("Authorization", "Bearer " + token)
    .timeout(Duration.ofSeconds(10))
    .GET()
    .build();

HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
```

**UnsupportedPlatformException** (already exists in common module):
- `com.aicodereview.common.exception.UnsupportedPlatformException`
- Mapped to HTTP 400 Bad Request
- Reuse for unknown platform in Factory

**ObjectMapper** (from WebhookController):
```java
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

### File Structure (MUST follow)

```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/
│   └── enums/
│       └── GitPlatform.java                       ← NEW: GITHUB, GITLAB, AWS_CODECOMMIT + fromRepoUrl()
│
├── ai-code-review-common/src/test/java/com/aicodereview/common/
│   └── enums/
│       └── GitPlatformTest.java                   ← NEW: Enum + URL detection tests
│
├── ai-code-review-integration/src/main/java/com/aicodereview/integration/
│   ├── config/
│   │   └── GitClientConfig.java                   ← NEW: HttpClient @Bean with timeout config
│   └── git/
│       ├── GitPlatformClient.java                 ← NEW: Interface
│       ├── GitHubApiClient.java                   ← NEW: @Component, GitHub REST API
│       ├── GitLabApiClient.java                   ← NEW: @Component, GitLab REST API
│       ├── AWSCodeCommitClient.java               ← NEW: @Component, Stub
│       └── GitPlatformClientFactory.java          ← NEW: @Component, Factory pattern
│
├── ai-code-review-integration/src/test/java/com/aicodereview/integration/
│   └── git/
│       ├── GitHubApiClientTest.java               ← NEW: Mock HttpClient tests
│       ├── GitLabApiClientTest.java               ← NEW: Mock HttpClient tests
│       ├── AWSCodeCommitClientTest.java           ← NEW: Stub verification tests
│       └── GitPlatformClientFactoryTest.java      ← NEW: Factory selection tests
│
├── ai-code-review-api/src/main/resources/
│   └── application.yml                            ← MODIFY: Add git.platform.* properties
```

### Implementation Patterns

**GitPlatform enum with URL detection:**
```java
public enum GitPlatform {
    GITHUB, GITLAB, AWS_CODECOMMIT;

    public static GitPlatform fromRepoUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isEmpty()) return null;
        String lower = repoUrl.toLowerCase();
        if (lower.contains("github.com")) return GITHUB;
        if (lower.contains("gitlab")) return GITLAB;
        if (lower.contains("codecommit")) return AWS_CODECOMMIT;
        return null; // or throw UnsupportedPlatformException
    }
}
```

**GitPlatformClient interface:**
```java
public interface GitPlatformClient {
    String getFileContent(String repoUrl, String commitHash, String filePath);
    String getDiff(String repoUrl, String commitHash);
    String getDiff(String repoUrl, String baseBranch, String headBranch);
    GitPlatform getPlatform();
}
```

**GitHub URL parsing helper:**
```java
// "https://github.com/owner/repo" → "owner/repo"
// "https://github.com/owner/repo.git" → "owner/repo"
private String parseOwnerRepo(String repoUrl) {
    URI uri = URI.create(repoUrl);
    String path = uri.getPath(); // "/owner/repo" or "/owner/repo.git"
    if (path.startsWith("/")) path = path.substring(1);
    if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
    return path; // "owner/repo"
}
```

**GitHub Base64 file content decoding:**
```java
// GitHub /contents API returns JSON: { "content": "base64...", "encoding": "base64" }
JsonNode json = objectMapper.readTree(responseBody);
String base64Content = json.get("content").asText().replaceAll("\\s", "");
return new String(Base64.getDecoder().decode(base64Content), StandardCharsets.UTF_8);
```

**GitLab project ID from URL:**
```java
// "https://gitlab.com/namespace/project" → URL-encoded "namespace%2Fproject"
private String parseProjectPath(String repoUrl) {
    URI uri = URI.create(repoUrl);
    String path = uri.getPath();
    if (path.startsWith("/")) path = path.substring(1);
    if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
    return URLEncoder.encode(path, StandardCharsets.UTF_8);
}
```

**GitLab commit diff response → unified diff assembly:**
```java
// GitLab /commits/{sha}/diff returns JSON array:
// [{"old_path":"a.java","new_path":"a.java","diff":"@@ -1,3 +1,4 @@\n ...","new_file":false,"renamed_file":false,"deleted_file":false}, ...]
// Must assemble into unified diff format for DiffMetadataExtractor:
StringBuilder unified = new StringBuilder();
for (JsonNode file : diffArray) {
    String oldPath = file.get("old_path").asText();
    String newPath = file.get("new_path").asText();
    boolean isNew = file.get("new_file").asBoolean();
    boolean isDeleted = file.get("deleted_file").asBoolean();
    boolean isRenamed = file.get("renamed_file").asBoolean();

    unified.append("diff --git a/").append(oldPath).append(" b/").append(newPath).append("\n");
    if (isNew) unified.append("new file mode 100644\n");
    if (isDeleted) unified.append("deleted file mode 100644\n");
    if (isRenamed) {
        unified.append("rename from ").append(oldPath).append("\n");
        unified.append("rename to ").append(newPath).append("\n");
    }
    unified.append("--- ").append(isNew ? "/dev/null" : "a/" + oldPath).append("\n");
    unified.append("+++ ").append(isDeleted ? "/dev/null" : "b/" + newPath).append("\n");
    unified.append(file.get("diff").asText()).append("\n");
}
return unified.toString();
```

**Retry logic pattern:**
```java
private <T> T executeWithRetry(Supplier<HttpResponse<T>> requestSupplier, int maxRetries) {
    int attempt = 0;
    while (true) {
        HttpResponse<T> response = requestSupplier.get();
        int status = response.statusCode();
        if (status >= 200 && status < 300) return response.body();
        if ((status == 429 || status >= 500) && attempt < maxRetries) {
            attempt++;
            Thread.sleep((long) Math.pow(2, attempt - 1) * 1000); // 1s, 2s
            continue;
        }
        throw new GitApiException(status, "API call failed: HTTP " + status);
    }
}
```

**Factory pattern:**
```java
@Component
public class GitPlatformClientFactory {
    private final Map<GitPlatform, GitPlatformClient> clientMap;

    public GitPlatformClientFactory(List<GitPlatformClient> clients) {
        this.clientMap = clients.stream()
            .collect(Collectors.toMap(GitPlatformClient::getPlatform, c -> c));
    }

    public GitPlatformClient getClient(GitPlatform platform) {
        GitPlatformClient client = clientMap.get(platform);
        if (client == null) throw new UnsupportedPlatformException("...");
        return client;
    }

    public GitPlatformClient getClient(String repoUrl) {
        GitPlatform platform = GitPlatform.fromRepoUrl(repoUrl);
        if (platform == null) throw new UnsupportedPlatformException("...");
        return getClient(platform);
    }
}
```

### Critical Patterns from Previous Stories (MUST follow)

1. **-parameters compiler flag NOT enabled**: Use `@PathVariable("id")` with explicit value. Not directly relevant here (no controllers), but maintain awareness.
2. **Enum test pattern**: Follow `ChangeTypeTest`, `FailureTypeTest` — assert enum size, assert specific values/mappings.
3. **Lombok DTOs**: Use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` for any new DTOs.
4. **No integration tests needed for external APIs**: Unit tests with mocked HttpClient suffice. Real API calls are for E2E tests (Epic 9).
5. **`@Component` for auto-discovery**: Follow webhook verifier pattern — each client is a `@Component`, Factory collects via `List<GitPlatformClient>` constructor injection.
6. **HttpClient as bean**: Inject `HttpClient` via constructor for testability, don't use static final field.

### Previous Story Intelligence (Story 3.1)

**Learnings from Story 3.1:**
- `DiffMetadataExtractor` expects raw Git Unified Diff format — API clients MUST return standard diff format
- GitLab `/commits/{sha}/diff` returns JSON array (NOT unified diff) — must be assembled into unified diff string
- GitHub diff APIs with `Accept: application/vnd.github.diff` return raw unified diff — can be passed directly
- `splitIntoFileSections` regex bug was fixed — diff content MUST start each file with `diff --git` prefix
- Binary file path extraction was added as fallback from `diff --git a/X b/Y` header

**Files created in Story 3.1** (integration points):
- `DiffMetadataExtractor.java` — will consume the diff strings this story's API clients produce
- `DiffMetadata`, `FileDiffInfo`, `DiffStatistics` DTOs — output of metadata extraction
- `ChangeType`, `Language` enums — used by metadata extraction

### Edge Cases to Handle

1. **Empty/null repoUrl or commitHash** → Throw `IllegalArgumentException`
2. **GitHub rate limiting (HTTP 429)** → Retry with exponential backoff
3. **Authentication failure (HTTP 401/403)** → Throw specific exception, do NOT retry
4. **File not found (HTTP 404)** → Throw specific exception, do NOT retry
5. **Server error (HTTP 500/502/503)** → Retry up to 2 times
6. **Large file content** → GitHub API has 1MB limit for contents endpoint; note in logs
7. **Binary file content** → Return null or empty string (AI doesn't need binary content)
8. **URL with/without .git suffix** → `parseOwnerRepo` must handle both
9. **GitLab self-hosted** → Use configurable base URL (not hardcoded `gitlab.com`)
10. **Network timeout** → HttpTimeoutException should be caught and wrapped
11. **Invalid JSON response** → Catch JsonProcessingException, wrap with context

### Dependencies

**No new Maven dependencies required for GitHub/GitLab clients:**
- `java.net.http.HttpClient` — built-in JDK 11+
- `com.fasterxml.jackson.core:jackson-databind` — already in common module
- `java.util.Base64` — built-in JDK

**Integration module pom.xml may need:**
- Add `jackson-databind` dependency if not already inherited
- Verify `ai-code-review-common` dependency is listed

### References

- [Source: epics.md — Epic 3: 代码获取与AI审查上下文组装 — Story 3.2 full specifications]
- [Source: architecture.md — Integration Module Structure — `git/` directory for Git operations]
- [Source: architecture.md — Error Handling Patterns — retry, exception hierarchy]
- [Source: architecture.md — Configuration Management — encrypted fields, environment variables]
- [Source: 3-1-diff-metadata-extraction.md — DiffMetadataExtractor integration, unified diff format requirements]
- [Source: WebhookVerificationChain.java — Chain/Factory pattern with `List<>` constructor injection]
- [Source: AiModelConfigServiceImpl.java — HttpClient usage pattern]
- [Source: commit 9f6082f — Story 3.1 implementation, diff parsing patterns]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Mockito generics fix: `HttpClient.send(any(), any())` returns `HttpResponse<Object>` due to type erasure. Fixed by using `doReturn().when()` pattern instead of `when().thenReturn()`.
- API module integration tests (ReviewTaskIntegrationTest) fail without Docker — pre-existing, not related to Story 3.2 changes.

### Completion Notes List

- All 10 ACs satisfied
- 57 new tests added (10 + 18 + 17 + 4 + 8)
- Total test counts: 97 common + 108 integration = 205 (0 failures)
- AWS CodeCommit client is a stub (throws UnsupportedOperationException) per design
- GitLab diff assembly converts JSON array to unified diff format compatible with DiffMetadataExtractor

### Code Review Follow-ups (applied)

| Severity | Issue | Fix |
|----------|-------|-----|
| HIGH | GitHubApiClient.getFileContent filePath未URL编码 | 添加 `encodeFilePath()` 逐段编码保留 `/` |
| MEDIUM | assembleUnifiedDiffFromArray JSON字段无null保护 | 改用 `file.path().asText("")` + 跳过空路径条目 |
| MEDIUM | GitLab compare URL分支名未编码 | 使用 `URLEncoder.encode()` 编码query参数 |
| MEDIUM | Story File List计数错误 (9→8) | 已修正 |

### File List

**New Source Files (8):**
1. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/GitPlatform.java`
2. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/exception/GitApiException.java`
3. `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitPlatformClient.java`
4. `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/config/GitClientConfig.java`
5. `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitHubApiClient.java`
6. `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitLabApiClient.java`
7. `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/AWSCodeCommitClient.java`
8. `backend/ai-code-review-integration/src/main/java/com/aicodereview/integration/git/GitPlatformClientFactory.java`

**New Test Files (5):**
9. `backend/ai-code-review-common/src/test/java/com/aicodereview/common/enums/GitPlatformTest.java`
10. `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/git/GitHubApiClientTest.java`
11. `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/git/GitLabApiClientTest.java`
12. `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/git/AWSCodeCommitClientTest.java`
13. `backend/ai-code-review-integration/src/test/java/com/aicodereview/integration/git/GitPlatformClientFactoryTest.java`

**Modified Files (1):**
14. `backend/ai-code-review-api/src/main/resources/application.yml` — Added `git.platform.*` configuration

### Change Log

| Change | File | Description |
|--------|------|-------------|
| ADD | GitPlatform.java | Enum with GITHUB, GITLAB, AWS_CODECOMMIT + fromRepoUrl() URL detection |
| ADD | GitApiException.java | RuntimeException with statusCode field for API error wrapping |
| ADD | GitPlatformClient.java | Interface: getFileContent, getDiff (2 overloads), getPlatform |
| ADD | GitClientConfig.java | @Configuration, @Bean HttpClient with configurable connect timeout |
| ADD | GitHubApiClient.java | GitHub REST API client: Bearer auth, Base64 decode, retry on 429/5xx |
| ADD | GitLabApiClient.java | GitLab REST API client: PRIVATE-TOKEN auth, unified diff assembly, configurable base URL |
| ADD | AWSCodeCommitClient.java | Stub: all methods throw UnsupportedOperationException |
| ADD | GitPlatformClientFactory.java | Factory: auto-discovers clients via List injection, Map lookup |
| MODIFY | application.yml | Added git.platform.connect-timeout-seconds, github.token, gitlab.token, gitlab.base-url |
| ADD | GitPlatformTest.java | 10 tests: enum count, URL detection, null/empty handling |
| ADD | GitHubApiClientTest.java | 18 tests: URL construction, Base64, filePath encoding, auth, errors, retry, empty token |
| ADD | GitLabApiClientTest.java | 17 tests: URL encoding, diff assembly, branch encoding, auth, self-hosted, retry |
| ADD | AWSCodeCommitClientTest.java | 4 tests: platform type, 3 methods throw UnsupportedOperationException |
| ADD | GitPlatformClientFactoryTest.java | 8 tests: client selection by platform/URL, unknown platform, empty factory |
