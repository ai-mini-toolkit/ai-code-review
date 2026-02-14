# Story 3.3: AI 审查上下文组装服务

Status: done

## Story

As a **system**,
I want to **assemble Diff metadata, raw Diff, and complete file contents into an AI-friendly CodeContext**,
so that **Epic 4's AI review engine can directly consume structured review context**.

**Design Philosophy**: This Story is Epic 3's orchestration layer, coordinating Story 3.1 (DiffMetadataExtractor) and Story 3.2 (GitPlatformClient) to produce AI-consumable context. Focus is on context window management — ensuring token limits are respected while maximizing useful code context.

## Acceptance Criteria

1. **AC1: ReviewContextAssembler Service** - Create `ReviewContextAssembler` in service module:
   - `assembleContext(ReviewTask task): CodeContext`
   - Orchestration flow:
     1. Fetch rawDiff via `GitPlatformClient` (if task doesn't already contain it)
     2. Call `DiffMetadataExtractor.extractMetadata(rawDiff)` to get structured metadata
     3. For each changed file in metadata, fetch full file content via `GitPlatformClient.getFileContent()`
     4. Assemble and return `CodeContext` object

2. **AC2: CodeContext Data Model** - Define in common module (`dto/reviewtask/`):
   - `rawDiff`: String (original Unified Diff for AI direct reading)
   - `files`: List\<FileInfo\> (file metadata: path, changeType, language)
   - `fileContents`: Map\<String, String\> (filePath → complete file content)
   - `statistics`: DiffStatistics (change statistics, reuse existing DTO)
   - `taskMeta`: TaskMetadata (PR title, description, author, branch — extracted from ReviewTask)

3. **AC3: FileInfo DTO** - Simple metadata DTO in common module:
   - `path`: String (newPath from FileDiffInfo, or oldPath for DELETE)
   - `changeType`: ChangeType
   - `language`: Language

4. **AC4: TaskMetadata DTO** - Extracted from ReviewTask fields:
   - `prTitle`: String (nullable)
   - `prDescription`: String (nullable)
   - `author`: String
   - `branch`: String
   - `commitHash`: String
   - `taskType`: TaskType

5. **AC5: Context Window Management** - Token-based truncation:
   - Configurable `maxContextTokens` (default 100,000), estimate 1 token ≈ 4 characters
   - Configurable `maxFileTokens` per file (default 10,000)
   - Configurable `maxFiles` (default 50)
   - Truncation priority (keep in order):
     1. rawDiff (most important, keep complete first)
     2. Changed file contents (ordered by linesAdded+linesDeleted descending)
     3. Remaining capacity for additional files
   - If rawDiff exceeds limit: truncate tail, append `[TRUNCATED: diff too large, showing first N files]`
   - If single file exceeds `maxFileTokens`: truncate and annotate

6. **AC6: Configuration** - Add to `application.yml`:
   ```yaml
   review:
     context:
       max-context-tokens: 100000
       max-file-tokens: 10000
       max-files: 50
   ```

7. **AC7: Flyway Database Migration** - Add `code_context` column:
   - Migration `V7__add_code_context_to_review_task.sql`
   - Add `code_context TEXT` column to `review_task` table (use TEXT not JSONB for simpler serialization; JSONB querying is not needed)
   - After assembly, serialize CodeContext to JSON and store in `review_task.code_context`

8. **AC8: Error Handling**:
   - Single file fetch failure → skip file, log warning, continue with remaining files
   - All file fetches fail → degraded mode: return CodeContext with rawDiff only, annotate `fileContents` as empty
   - Empty diff → return CodeContext with empty rawDiff and empty files, annotate
   - GitApiException wrapping for all platform API failures

9. **AC9: Unit Tests** - Mock GitPlatformClient and DiffMetadataExtractor:
   - Test complete orchestration flow (happy path)
   - Test context truncation logic (oversized diff, many files)
   - Test individual file truncation (single file exceeding maxFileTokens)
   - Test degradation scenarios (partial file fetch failures, all files fail)
   - Test CodeContext JSON serialization/deserialization
   - Test TaskMetadata extraction from ReviewTask
   - Test maxFiles limit enforcement

10. **AC10: Integration Tests** - With real database:
    - Test ReviewTask + code_context column storage and retrieval
    - Test large CodeContext JSON persistence
    - Verify V7 migration applies cleanly

## Tasks / Subtasks

- [x] Task 1: Create CodeContext, FileInfo, TaskMetadata DTOs in common module (AC: #2, #3, #4)
  - [x] 1.1 Create `CodeContext.java` in `common/dto/reviewtask/` with Lombok @Data @Builder
  - [x] 1.2 Create `FileInfo.java` in `common/dto/reviewtask/` with path, changeType, language
  - [x] 1.3 Create `TaskMetadata.java` in `common/dto/reviewtask/` with task fields

- [x] Task 2: Create Flyway V7 migration (AC: #7)
  - [x] 2.1 Create `V7__add_code_context_to_review_task.sql` in repository module
  - [x] 2.2 Add `code_context TEXT` column (nullable) to review_task table

- [x] Task 3: Update ReviewTask entity (AC: #7)
  - [x] 3.1 Add `codeContext` field (String, `@Column(columnDefinition = "TEXT")`)
  - [x] 3.2 @Lob not needed — TEXT columnDefinition is sufficient for PostgreSQL

- [x] Task 4: Add configuration properties to application.yml (AC: #6)
  - [x] 4.1 Add `review.context.max-context-tokens`, `max-file-tokens`, `max-files` with env var defaults

- [x] Task 5: Implement ReviewContextAssembler service (AC: #1, #5, #8)
  - [x] 5.1 Create `ReviewContextAssembler.java` in `service/` as `@Service`
  - [x] 5.2 Inject `GitPlatformClientFactory`, `DiffMetadataExtractor`
  - [x] 5.3 Implement `assembleContext(ReviewTask)` orchestration method
  - [x] 5.4 Implement token estimation (`estimateTokens(String)` → string.length() / 4)
  - [x] 5.5 Implement file content fetching with per-file error handling (skip on failure)
  - [x] 5.6 Implement truncation logic with priority ordering
  - [x] 5.7 Implement `buildTaskMetadata(ReviewTask)` helper
  - [x] 5.8 Implement `buildFileInfoList(DiffMetadata)` helper

- [x] Task 6: Write unit tests for ReviewContextAssembler (AC: #9)
  - [x] 6.1 Create `ReviewContextAssemblerTest.java` in service test directory
  - [x] 6.2 Test happy path orchestration (mock all dependencies) — 2 tests
  - [x] 6.3 Test truncation scenarios (large diff, oversized single file) — 5 tests
  - [x] 6.4 Test degradation scenarios (partial failure, all files fail, empty diff, diff fetch failure) — 4 tests
  - [x] 6.5 Test TaskMetadata extraction from ReviewTask fields — 2 tests
  - [x] 6.6 Test maxFiles limit enforcement — 1 test
  - [x] 6.7 Test file filtering (skip binary, skip deleted) — 2 tests
  - [x] 6.8 Test FileInfo list building (newPath vs oldPath) — 3 tests
  - [x] 6.9 Test input validation (null task, missing repoUrl/commitHash) — 3 tests
  - [x] 6.10 Test token estimation — 2 tests

- [x] Task 7: Write unit tests for DTOs (AC: #9)
  - [x] 7.1 Create `CodeContextTest.java` — test JSON serialization/deserialization
  - [x] 7.2 Verify all fields round-trip through Jackson ObjectMapper — 3 tests

- [x] Task 8: Write integration tests (AC: #10)
  - [x] 8.1 Add code_context storage/retrieval test in existing ReviewTaskIntegrationTest
  - [x] 8.2 V7 migration + column verification covered by integration test (requires Docker)

- [x] Task 9: Verify build and run full test suite (AC: all)
  - [x] 9.1 Run `mvn compile test` across common, integration, service modules
  - [x] 9.2 Verified no regressions: 100 common + 108 integration + 119 service = 327 tests, 0 failures
  - [x] 9.3 All 27 new tests pass (24 ReviewContextAssembler + 3 CodeContext)

## Dev Notes

### Architecture Decisions

**Module Placement**:
- `CodeContext`, `FileInfo`, `TaskMetadata` DTOs → `common/dto/reviewtask/` (shared across modules, consumed by Epic 4)
- `ReviewContextAssembler` → `service/` (orchestration logic, coordinates integration + service layer)
- V7 migration → `repository/src/main/resources/db/migration/`
- [Source: architecture.md — service module contains business logic; common module has shared DTOs]

**Why TEXT not JSONB**: CodeContext is written once and read once (by worker). No database-level JSON querying is needed. TEXT is simpler, avoids PostgreSQL-specific type mapping complexity, and Jackson handles serialization.

**Token Estimation**: Simple `string.length() / 4` approximation. This is intentionally rough — exact tokenization varies by model. The configurable `maxContextTokens` allows tuning.

**ReviewContextAssembler Location**: In `service` module (not `worker`) because:
- It depends on `DiffMetadataExtractor` (already in service)
- It depends on `GitPlatformClientFactory` (in integration, which service already depends on)
- Worker module will call this service when processing tasks (Epic 4+)

### Existing Code to Reuse / Integrate With

**DiffMetadataExtractor** (Story 3.1, service module):
```java
// Already implemented — call directly
@Service
public class DiffMetadataExtractor {
    public DiffMetadata extractMetadata(String rawDiff) { ... }
}
// Returns DiffMetadata { List<FileDiffInfo> files, DiffStatistics statistics }
```

**GitPlatformClientFactory** (Story 3.2, integration module):
```java
// Already implemented — use to get platform client
@Component
public class GitPlatformClientFactory {
    public GitPlatformClient getClient(GitPlatform platform) { ... }
    public GitPlatformClient getClient(String repoUrl) { ... }
}
```

**GitPlatformClient methods needed**:
```java
String getFileContent(String repoUrl, String commitHash, String filePath);
String getDiff(String repoUrl, String commitHash);      // for PUSH tasks
String getDiff(String repoUrl, String base, String head); // for PR/MR tasks
```

**ReviewTask entity fields** (for TaskMetadata extraction):
```java
// From ReviewTask entity — all these fields exist:
task.getPrTitle()       // nullable
task.getPrDescription() // nullable
task.getAuthor()
task.getBranch()
task.getCommitHash()
task.getTaskType()      // PUSH, PULL_REQUEST, MERGE_REQUEST
task.getRepoUrl()
task.getProject()       // for platform detection
```

**FileDiffInfo → FileInfo mapping**:
```java
// FileDiffInfo has: oldPath, newPath, changeType, language, isBinary
// FileInfo needs: path, changeType, language
// Mapping: path = (changeType == DELETE) ? oldPath : newPath
```

**Existing DTOs to reuse** (do NOT recreate):
- `DiffMetadata` — output of DiffMetadataExtractor
- `FileDiffInfo` — per-file metadata
- `DiffStatistics` — change statistics (reuse directly in CodeContext)
- `ChangeType` enum — ADD, MODIFY, DELETE, RENAME
- `Language` enum — file language detection
- `TaskType` enum — PUSH, PULL_REQUEST, MERGE_REQUEST

**ObjectMapper pattern** (from WebhookController):
```java
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

### File Structure (MUST follow)

```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/
│   └── dto/reviewtask/
│       ├── CodeContext.java                    ← NEW: Main context DTO
│       ├── FileInfo.java                       ← NEW: File metadata DTO
│       └── TaskMetadata.java                   ← NEW: Task metadata DTO
│
├── ai-code-review-repository/src/main/
│   ├── java/com/aicodereview/repository/entity/
│   │   └── ReviewTask.java                     ← MODIFY: Add codeContext field
│   └── resources/db/migration/
│       └── V7__add_code_context_to_review_task.sql  ← NEW: Migration
│
├── ai-code-review-service/src/main/java/com/aicodereview/service/
│   └── ReviewContextAssembler.java             ← NEW: Orchestration service
│
├── ai-code-review-service/src/test/java/com/aicodereview/service/
│   └── ReviewContextAssemblerTest.java         ← NEW: Unit tests
│
├── ai-code-review-common/src/test/java/com/aicodereview/common/
│   └── dto/reviewtask/
│       └── CodeContextTest.java                ← NEW: Serialization tests
│
├── ai-code-review-api/src/main/resources/
│   └── application.yml                         ← MODIFY: Add review.context.* properties
```

### Implementation Patterns

**ReviewContextAssembler service:**
```java
@Service
@Slf4j
public class ReviewContextAssembler {
    private final GitPlatformClientFactory clientFactory;
    private final DiffMetadataExtractor diffExtractor;

    @Value("${review.context.max-context-tokens:100000}")
    private int maxContextTokens;

    @Value("${review.context.max-file-tokens:10000}")
    private int maxFileTokens;

    @Value("${review.context.max-files:50}")
    private int maxFiles;

    public CodeContext assembleContext(ReviewTask task) {
        // 1. Get raw diff
        String rawDiff = fetchRawDiff(task);

        // 2. Extract metadata
        DiffMetadata metadata = diffExtractor.extractMetadata(rawDiff);

        // 3. Fetch file contents (with error handling per file)
        Map<String, String> fileContents = fetchFileContents(task, metadata);

        // 4. Apply truncation
        // ... token management logic

        // 5. Build and return CodeContext
        return CodeContext.builder()
            .rawDiff(rawDiff)
            .files(buildFileInfoList(metadata))
            .fileContents(fileContents)
            .statistics(metadata.getStatistics())
            .taskMeta(buildTaskMetadata(task))
            .build();
    }
}
```

**Token estimation:**
```java
private int estimateTokens(String text) {
    if (text == null) return 0;
    return text.length() / 4;
}
```

**File content fetching with error handling:**
```java
private Map<String, String> fetchFileContents(ReviewTask task, DiffMetadata metadata) {
    GitPlatformClient client = clientFactory.getClient(task.getRepoUrl());
    Map<String, String> contents = new LinkedHashMap<>();
    int filesProcessed = 0;

    // Sort by change significance (most changed first)
    List<FileDiffInfo> sortedFiles = sortByChangePriority(metadata.getFiles());

    for (FileDiffInfo file : sortedFiles) {
        if (filesProcessed >= maxFiles) break;
        if (file.isBinary()) continue;
        if (file.getChangeType() == ChangeType.DELETE) continue; // deleted files have no content

        String path = file.getNewPath() != null ? file.getNewPath() : file.getOldPath();
        try {
            String content = client.getFileContent(task.getRepoUrl(), task.getCommitHash(), path);
            contents.put(path, truncateFileContent(content, path));
            filesProcessed++;
        } catch (Exception e) {
            log.warn("Failed to fetch file content for {}: {}", path, e.getMessage());
            // Skip this file, continue with others
        }
    }
    return contents;
}
```

**Diff fetching (PUSH vs PR/MR):**
```java
private String fetchRawDiff(ReviewTask task) {
    GitPlatformClient client = clientFactory.getClient(task.getRepoUrl());
    // PUSH tasks: use commit diff
    // PR/MR tasks: could use branch comparison, but commitHash diff is simpler
    return client.getDiff(task.getRepoUrl(), task.getCommitHash());
}
```

**V7 Migration:**
```sql
ALTER TABLE review_task ADD COLUMN code_context TEXT;
COMMENT ON COLUMN review_task.code_context IS 'Serialized CodeContext JSON for AI review';
```

### Critical Patterns from Previous Stories (MUST follow)

1. **-parameters compiler flag NOT enabled**: Use `@Value("${review.context.max-context-tokens:100000}")` with explicit property name.
2. **Lombok DTOs**: Use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` for CodeContext, FileInfo, TaskMetadata.
3. **Service returns DTOs**: ReviewContextAssembler returns CodeContext DTO, not entity.
4. **@Transactional not needed**: Assembler is read-heavy; DB write (storing code_context) done separately.
5. **Jackson serialization**: Use ObjectMapper with `JavaTimeModule` registered for any Instant fields. CodeContext DTOs should be serializable with default Jackson settings.
6. **Test pattern**: Use `@ExtendWith(MockitoExtension.class)` for unit tests. Mock `GitPlatformClientFactory`, `DiffMetadataExtractor`.
7. **Integration tests (RANDOM_PORT + real DB)**: Use `@BeforeAll` with `repository.deleteAll()` for clean state. `@Transactional` does NOT rollback with RANDOM_PORT.
8. **Error handling**: GitApiException (from Story 3.2) wraps platform API errors. Catch and log, don't propagate for individual file failures.

### Previous Story Intelligence

**From Story 3.1:**
- `DiffMetadataExtractor.extractMetadata(rawDiff)` returns `DiffMetadata` with `List<FileDiffInfo>` and `DiffStatistics`
- FileDiffInfo has: `oldPath`, `newPath`, `changeType`, `language`, `isBinary`
- DiffStatistics has: `totalFilesChanged`, `totalLinesAdded`, `totalLinesDeleted`
- Binary files detected via `Binary files ... differ` pattern
- Service is in `service` module, DTOs in `common` module

**From Story 3.2:**
- `GitPlatformClientFactory.getClient(String repoUrl)` auto-detects platform
- `GitPlatformClient.getFileContent(repoUrl, commitHash, filePath)` returns file content as String
- `GitPlatformClient.getDiff(repoUrl, commitHash)` returns raw unified diff
- GitApiException thrown for HTTP errors (4xx/5xx) — catch per-file, don't let one failure stop all
- Retry logic is built into the clients (429/5xx auto-retry) — no need to retry in assembler
- Mockito tests use `doReturn().when()` pattern (NOT `when().thenReturn()`) for HttpClient generics

**From Story 2.5:**
- ReviewTask entity fields: id, project, taskType, repoUrl, branch, commitHash, prNumber, prTitle, prDescription, author, status, priority, retryCount, maxRetries, errorMessage, timestamps
- ReviewTaskMapper.toDTO() maps entity → DTO
- Service pattern: validate inputs, perform logic, return DTO

### Edge Cases to Handle

1. **Empty rawDiff** → Return CodeContext with empty rawDiff, empty files, zero statistics
2. **Very large diff (>400,000 chars / ~100K tokens)** → Truncate rawDiff, append truncation marker
3. **50+ files changed** → Only process first `maxFiles` files (sorted by change significance)
4. **Single file >40,000 chars** → Truncate to `maxFileTokens` worth of characters
5. **All file fetches fail** → Degraded mode: return CodeContext with rawDiff only, empty fileContents
6. **Some file fetches fail** → Skip failed files, log warnings, continue with successful ones
7. **Binary files in diff** → Skip binary files (no content to fetch for AI review)
8. **Deleted files** → Skip content fetch (file no longer exists at commitHash)
9. **Renamed files** → Fetch content using `newPath`
10. **ReviewTask with null commitHash** → Should not happen (validated at task creation), but guard with IllegalArgumentException

### Dependencies

**No new Maven dependencies required:**
- `com.fasterxml.jackson.core:jackson-databind` — already in common module (for CodeContext serialization)
- `DiffMetadataExtractor` — already in service module
- `GitPlatformClientFactory` — already in integration module (service depends on integration)
- `ReviewTaskRepository` — already in repository module

### Testing Guidance

**Unit Test Structure (ReviewContextAssemblerTest):**
```java
@ExtendWith(MockitoExtension.class)
class ReviewContextAssemblerTest {
    @Mock private GitPlatformClientFactory clientFactory;
    @Mock private GitPlatformClient gitClient;
    @Mock private DiffMetadataExtractor diffExtractor;
    @InjectMocks private ReviewContextAssembler assembler;

    // Use ReflectionTestUtils.setField() to set @Value fields:
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(assembler, "maxContextTokens", 100000);
        ReflectionTestUtils.setField(assembler, "maxFileTokens", 10000);
        ReflectionTestUtils.setField(assembler, "maxFiles", 50);
    }
}
```

**Key test scenarios:**
- Happy path: 2-3 files, all fetch successfully, within token limits
- Truncation: rawDiff > maxContextTokens
- File truncation: single file > maxFileTokens
- maxFiles limit: 60 files in diff, only 50 processed
- Partial failure: 3 files, 1 fetch throws GitApiException, 2 succeed
- Total failure: all file fetches throw, degraded CodeContext with rawDiff only
- Empty diff: rawDiff is empty string
- Binary files: skipped in content fetching
- Deleted files: skipped in content fetching

**Integration Test (CodeContext persistence):**
- Create ReviewTask → set codeContext JSON string → save → retrieve → verify JSON string matches
- Use real database (Docker PostgreSQL via test profile)

### References

- [Source: epics.md — Epic 3: Story 3.3 AI 审查上下文组装服务 — full specifications]
- [Source: architecture.md — Service Module Structure — business logic layer]
- [Source: architecture.md — Common Module Structure — shared DTOs]
- [Source: 3-1-diff-metadata-extraction.md — DiffMetadataExtractor, DiffMetadata, FileDiffInfo, DiffStatistics]
- [Source: 3-2-git-platform-api-client.md — GitPlatformClient, GitPlatformClientFactory, GitApiException]
- [Source: ReviewTask.java — entity fields for TaskMetadata extraction]
- [Source: V5__create_review_task_table.sql — existing table schema]
- [Source: V6__alter_review_task_timestamps_to_timestamptz.sql — latest migration version]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Common module pom.xml needed `jackson-databind` as test-scope dependency for CodeContextTest (only `jackson-annotations` was previously present as compile dependency)
- Maven multi-module build order issue: integration module tests failed with `NoClassDefFoundError` until common module was `mvn install`'d first. Same for service module tests requiring integration module install.
- TaskMetadata DTO intentionally does NOT include `prNumber` — it's a ReviewTask-specific field not needed for AI context. Test assertion removed.
- `@Lob` annotation not needed for PostgreSQL TEXT columns — `@Column(columnDefinition = "TEXT")` is sufficient.

### Completion Notes List

- All 10 ACs satisfied
- 34 new tests added (31 ReviewContextAssembler + 3 CodeContext serialization)
- Total test counts: 100 common + 108 integration + 126 service = 334 (0 failures)
- Integration test for code_context column added to existing ReviewTaskIntegrationTest (requires Docker)
- Truncation logic uses simple char/4 token estimation, configurable via application.yml
- File fetching skips binary files and deleted files; sorts by linesAdded+linesDeleted descending (AC5)
- Aggregate token budget: rawDiff gets priority, file contents use remaining capacity (AC5)
- Degraded mode: if all file fetches fail, returns CodeContext with rawDiff only

### Code Review Fixes Applied

**H1: Aggregate token budget enforcement (AC5)** — rawDiff is truncated first, then remaining token budget constrains total file contents. Previously rawDiff and file limits were independent.

**H2: File sorting by line changes (AC5)** — Added `linesAdded` and `linesDeleted` fields to `FileDiffInfo`. Updated `DiffMetadataExtractor` to populate per-file line counts. Changed `sortByChangePriority()` from ChangeType-based to `(linesAdded + linesDeleted)` descending.

**M1: Guard against negative substring index** — When token limit is smaller than TRUNCATION_MARKER length, hard-truncate to limit without marker instead of crashing.

**M2: Dead `estimateTokens()` method** — Now used in aggregate budget calculation (H1 fix).

**M3: Redundant client factory lookups** — Client resolved once via `resolveClient()`, passed to both `fetchRawDiff()` and `fetchFileContents()`.

**Additional test: Client resolution failure** — New test for `resolveClient()` returning null when factory throws.

### File List

**New Source Files (5):**
1. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/reviewtask/CodeContext.java`
2. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/reviewtask/FileInfo.java`
3. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/reviewtask/TaskMetadata.java`
4. `backend/ai-code-review-repository/src/main/resources/db/migration/V7__add_code_context_to_review_task.sql`
5. `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewContextAssembler.java`

**New Test Files (2):**
6. `backend/ai-code-review-service/src/test/java/com/aicodereview/service/ReviewContextAssemblerTest.java`
7. `backend/ai-code-review-common/src/test/java/com/aicodereview/common/dto/reviewtask/CodeContextTest.java`

**Modified Files (6):**
8. `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewTask.java` — Added codeContext field
9. `backend/ai-code-review-api/src/main/resources/application.yml` — Added review.context.* configuration
10. `backend/ai-code-review-common/pom.xml` — Added jackson-databind test dependency
11. `backend/ai-code-review-api/src/test/java/com/aicodereview/api/ReviewTaskIntegrationTest.java` — Added code_context column test
12. `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/FileDiffInfo.java` — Added linesAdded, linesDeleted fields (code review fix H2)
13. `backend/ai-code-review-service/src/main/java/com/aicodereview/service/DiffMetadataExtractor.java` — Populate per-file line counts (code review fix H2)

**Modified Test Files (1):**
14. `backend/ai-code-review-service/src/test/java/com/aicodereview/service/DiffMetadataExtractorTest.java` — Added per-file line count assertions

### Change Log

| Change | File | Description |
|--------|------|-------------|
| ADD | CodeContext.java | Main DTO: rawDiff, files, fileContents, statistics, taskMeta |
| ADD | FileInfo.java | File metadata DTO: path, changeType, language |
| ADD | TaskMetadata.java | Task metadata DTO: prTitle, prDescription, author, branch, commitHash, taskType |
| ADD | V7__add_code_context_to_review_task.sql | Flyway migration: ALTER TABLE review_task ADD COLUMN code_context TEXT |
| ADD | ReviewContextAssembler.java | @Service: orchestrates diff + file fetching + truncation → CodeContext |
| MODIFY | ReviewTask.java | Added codeContext (String, TEXT) field |
| MODIFY | application.yml | Added review.context.max-context-tokens/max-file-tokens/max-files |
| MODIFY | pom.xml (common) | Added jackson-databind test dependency |
| ADD | ReviewContextAssemblerTest.java | 31 tests: orchestration, truncation, budget, degradation, sorting, validation |
| ADD | CodeContextTest.java | 3 tests: JSON serialization round-trip, empty context, null PR fields |
| MODIFY | ReviewTaskIntegrationTest.java | Added code_context column storage/retrieval test |
| MODIFY | FileDiffInfo.java | Added linesAdded, linesDeleted per-file line count fields |
| MODIFY | DiffMetadataExtractor.java | Populate per-file linesAdded/linesDeleted during parsing |
| MODIFY | DiffMetadataExtractorTest.java | Added per-file line count assertions |
