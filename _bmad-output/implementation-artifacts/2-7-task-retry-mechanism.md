# Story 2.7: 任务重试机制 (Task Retry Mechanism)

Status: done

## Story

As a **system**,
I want to **implement a task failure retry mechanism with error classification, exponential backoff, and jitter**,
so that **transient failures (rate limits, network errors) are automatically retried with increasing delays, while non-retryable errors (validation, authentication) fail immediately, improving overall system reliability and review success rate**.

## Acceptance Criteria

1. **AC1: Error Type Classification** - Create `FailureType` enum to classify failure causes:
   - `RATE_LIMIT`: AI API 429 errors → retryable with exponential backoff
   - `NETWORK_ERROR`: Connection failures, DNS errors → retryable with exponential backoff
   - `TIMEOUT`: Request timeout (>30s) → retryable with exponential backoff
   - `VALIDATION_ERROR`: Invalid input, bad payload → NOT retryable, immediate FAILED
   - `AUTHENTICATION_ERROR`: 401/403 errors → NOT retryable, immediate FAILED
   - `UNKNOWN`: Unclassified errors → retryable with exponential backoff (conservative)

2. **AC2: Exponential Backoff Delay Calculation** - Calculate retry delay:
   - Formula: `2^retryCount` seconds (before the current retry attempt)
   - Delay sequence: 1s → 2s → 4s (for max 3 retries)
   - Add random jitter: 0-500ms to prevent thundering herd
   - Result: delay = `(int) Math.pow(2, retryCount)` + `random(0, 500)` ms

3. **AC3: RetryService Interface** - Create `RetryService` with core methods:
   - `handleTaskFailure(taskId, errorMessage, failureType)`: Orchestrates retry decision
   - `calculateRetryDelaySeconds(retryCount)`: Returns delay with exponential backoff + jitter
   - `isRetryable(failureType)`: Determines if error type allows retry

4. **AC4: RetryService Implementation** - `RetryServiceImpl` orchestrates retry flow:
   - For **retryable** errors: call `reviewTaskService.markTaskFailed()` (updates DB state), then `queueService.requeueWithDelay()` with calculated delay
   - For **non-retryable** errors: call `reviewTaskService.markTaskFailedPermanently()` (immediate FAILED, no retry)
   - Best-effort requeue: if Redis fails, DB state is still updated (consistent with Story 2.6 pattern)

5. **AC5: markTaskFailed() Enhancement** - Modify existing `markTaskFailed()`:
   - **Remove** the `queueService.enqueue()` call — requeue responsibility moves to `RetryService`
   - Keep existing DB state logic: increment retryCount, check max_retries, revert to PENDING or mark FAILED
   - Return updated DTO for caller to inspect new status

6. **AC6: markTaskFailedPermanently()** - New method for non-retryable errors:
   - Sets status = FAILED immediately (regardless of retry_count vs max_retries)
   - Sets completedAt timestamp
   - Sets errorMessage
   - Does NOT increment retry_count (error wasn't a "retry attempt")
   - Releases queue lock if held

7. **AC7: Unit Tests** - Comprehensive unit tests for all retry scenarios:
   - Error classification (each FailureType correctly classified)
   - Delay calculation (exponential backoff values, jitter range)
   - Retryable failure → markTaskFailed() + requeueWithDelay() called
   - Non-retryable failure → markTaskFailedPermanently() called, NO requeue
   - Max retries reached → FAILED status, NO requeue
   - Redis failure during requeue → DB state still saved (resilience)

8. **AC8: Integration Tests** - Integration tests with real Redis + DB:
   - Full retry lifecycle: fail → requeue with delay → dequeue after delay → retry
   - Non-retryable error → immediate FAILED with no requeue
   - Exponential backoff delay verification (task appears in queue after delay)
   - Max retries exhaustion → final FAILED status

## Tasks / Subtasks

- [x] Task 1: Create FailureType enum in common module (AC: #1)
  - [x] 1.1 Create `FailureType.java` in `common/enums/` with 6 failure types
  - [x] 1.2 Add `isRetryable()` method on the enum itself for self-describing classification
  - [x] 1.3 Add descriptive Javadoc for each failure type

- [x] Task 2: Create RetryService interface in service module (AC: #3)
  - [x] 2.1 Define interface with `handleTaskFailure()`, `calculateRetryDelaySeconds()`, `isRetryable()` methods
  - [x] 2.2 Add Javadoc documenting delay formula and error classification rules

- [x] Task 3: Implement RetryServiceImpl in service module (AC: #2, #4)
  - [x] 3.1 Inject ReviewTaskService and QueueService
  - [x] 3.2 Implement `isRetryable()`: true for RATE_LIMIT, NETWORK_ERROR, TIMEOUT, UNKNOWN; false for VALIDATION_ERROR, AUTHENTICATION_ERROR
  - [x] 3.3 Implement `calculateRetryDelaySeconds()`: `(int) Math.pow(2, retryCount)` + random jitter (0-500ms converted to seconds fraction → round to integer seconds minimum 1)
  - [x] 3.4 Implement `handleTaskFailure()` orchestration logic with best-effort Redis requeue
  - [x] 3.5 Add INFO/WARN/ERROR logging at key decision points

- [x] Task 4: Modify ReviewTaskServiceImpl (AC: #5, #6)
  - [x] 4.1 Remove `queueService.enqueue()` call from `markTaskFailed()` — RetryService handles requeue
  - [x] 4.2 Add `markTaskFailedPermanently(Long id, String errorMessage)` method
  - [x] 4.3 Update ReviewTaskService interface with new method signature

- [x] Task 5: Write unit tests for RetryServiceImpl (AC: #7)
  - [x] 5.1 Mock ReviewTaskService and QueueService
  - [x] 5.2 Test each FailureType classification (retryable vs non-retryable)
  - [x] 5.3 Test delay calculation: verify exponential backoff sequence (1s, 2s, 4s)
  - [x] 5.4 Test delay jitter: verify range 0-500ms
  - [x] 5.5 Test retryable failure → verifies markTaskFailed() + requeueWithDelay() called
  - [x] 5.6 Test non-retryable failure → verifies markTaskFailedPermanently() called, NO requeueWithDelay()
  - [x] 5.7 Test max retries reached → FAILED status, no requeue
  - [x] 5.8 Test Redis failure during requeue → DB state still persisted

- [x] Task 6: Update existing ReviewTaskServiceImplTest (AC: #5, #6, #7)
  - [x] 6.1 Remove `verify(queueService).enqueue(...)` from markTaskFailed retry tests
  - [x] 6.2 Add tests for `markTaskFailedPermanently()` (immediate FAILED, no retry_count change)
  - [x] 6.3 Verify existing markTaskFailed() behavior still correct (DB state only)

- [x] Task 7: Write integration tests (AC: #8)
  - [x] 7.1 Create `RetryIntegrationTest` with @SpringBootTest(RANDOM_PORT)
  - [x] 7.2 Test full retry lifecycle with real Redis and DB
  - [x] 7.3 Test non-retryable failure with real DB
  - [x] 7.4 Test delay-based requeue (verify task appears in queue after delay)

- [x] Task 8: Verify build and run full test suite (AC: all)
  - [x] 8.1 Run `mvn compile test` across all modules
  - [x] 8.2 Verify no regressions in existing tests (79 service + 5 retry integration = all pass)
  - [x] 8.3 Verify new tests pass (17 RetryServiceImplTest + 5 RetryIntegrationTest)

## Dev Notes

### Critical Architecture Decisions

**Error Classification Strategy** (from architecture.md Retry Strategy):
- **Retryable**: AI API rate limits (429), network errors, temporary unavailability (503), timeouts
- **Non-retryable**: Validation errors (400), authentication failures (401/403), business logic errors
- **Unknown errors**: Treat as retryable (conservative approach - better to retry than to lose tasks)
- [Source: architecture.md - Retry Strategy Implementation - Retry Rules]

**Exponential Backoff Formula** (from epics.md Story 2.7):
- Delay = `2^retry_count` seconds, where retry_count is the CURRENT count BEFORE this attempt
- Sequence: 1s (2^0), 2s (2^1), 4s (2^2) for max_retries=3
- Jitter: random 0-500ms added to prevent thundering herd
- [Source: epics.md - Story 2.7 验收标准]

> **NOTE**: Architecture.md shows `BACKOFF_DELAYS_MS = {1000, 2000, 4000}` (array-based). Epics.md says `2^retry_count seconds`. Both produce the same sequence: 1s, 2s, 4s. Use the formula approach (more flexible) but document the equivalence.

**Retry Service Placement** (temporary):
- Architecture.md places `RetryService` in `worker/processor/` module
- Worker module does NOT exist yet (will be built in Epic 3+)
- Place `RetryService` in **service module** for now (same pattern as Story 2.6 QueueService)
- Can refactor to worker module when TaskConsumer is implemented
- [Source: architecture.md - Worker Module Structure - processor/RetryService.java]

### Existing Code to Modify/Extend

**ReviewTaskServiceImpl.markTaskFailed()** (current behavior - Story 2.5/2.6):
```java
// Current: increments retry_count, checks max, re-enqueues via enqueue()
// Story 2.7 change: REMOVE the queueService.enqueue() call
// Requeue responsibility moves to RetryService (which uses requeueWithDelay)
```
Key file: `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewTaskServiceImpl.java`

**QueueService.requeueWithDelay()** (from Story 2.6 — already implemented):
```java
void requeueWithDelay(Long taskId, TaskPriority priority, int delaySeconds);
// Already works: releases lock, calculates future score, ZADD with delayed timestamp
// RetryService will call this with calculated exponential backoff delay
```
Key file: `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/RedisQueueService.java`

**QueueService.releaseLock()** (from Story 2.6 — already implemented):
```java
void releaseLock(Long taskId);
// Used by markTaskFailedPermanently() to clean up lock when permanently failing a task
```

### File Structure (MUST follow)

```
backend/
├── ai-code-review-common/src/main/java/com/aicodereview/common/
│   └── enums/
│       └── FailureType.java                    ← NEW: Error classification enum
│
├── ai-code-review-service/src/main/java/com/aicodereview/service/
│   ├── RetryService.java                       ← NEW: Interface
│   ├── ReviewTaskService.java                  ← MODIFY: Add markTaskFailedPermanently()
│   └── impl/
│       ├── RetryServiceImpl.java               ← NEW: Implementation
│       └── ReviewTaskServiceImpl.java          ← MODIFY: Remove enqueue from markTaskFailed(), add markTaskFailedPermanently()
│
├── ai-code-review-service/src/test/java/com/aicodereview/service/
│   └── impl/
│       ├── RetryServiceImplTest.java           ← NEW: Unit tests
│       └── ReviewTaskServiceImplTest.java      ← MODIFY: Update markTaskFailed() tests
│
└── ai-code-review-api/src/test/java/com/aicodereview/api/
    └── RetryIntegrationTest.java               ← NEW: Integration tests
```

### Implementation Patterns

**FailureType enum pattern:**
```java
public enum FailureType {
    RATE_LIMIT("AI API rate limit exceeded", true),
    NETWORK_ERROR("Network connection failure", true),
    TIMEOUT("Request timeout", true),
    VALIDATION_ERROR("Invalid input or payload", false),
    AUTHENTICATION_ERROR("Authentication or authorization failure", false),
    UNKNOWN("Unclassified error", true); // Conservative: treat unknown as retryable

    private final String description;
    private final boolean retryable;

    // Constructor, getters
    public boolean isRetryable() { return retryable; }
}
```

**RetryServiceImpl delay calculation:**
```java
public int calculateRetryDelaySeconds(int retryCount) {
    int baseDelay = (int) Math.pow(2, retryCount); // 1, 2, 4
    int jitterMs = ThreadLocalRandom.current().nextInt(0, 501); // 0-500ms
    // Convert jitter to seconds (round up to at least 1 second total)
    return baseDelay + (jitterMs >= 500 ? 1 : 0); // baseDelay + 0 or 1 second
}
```

> **NOTE**: Jitter of 0-500ms is sub-second. Since `requeueWithDelay()` accepts `int delaySeconds`, the jitter is best applied as a fractional addition. Two options:
> 1. Change requeueWithDelay to accept milliseconds (API change)
> 2. Apply jitter at the score level inside requeueWithDelay (internal change)
> 3. Round to seconds and accept 0-1s granularity loss
> **Recommendation**: Option 3 is simplest. Jitter prevents exact collision — even 1-second jitter spread across workers is sufficient for thundering herd prevention. Use `baseDelay` as-is and accept the coarse granularity for now.

**RetryServiceImpl handleTaskFailure() pattern:**
```java
public void handleTaskFailure(Long taskId, String errorMessage, FailureType failureType) {
    if (!failureType.isRetryable()) {
        log.warn("Non-retryable failure for task {}: type={}, error={}", taskId, failureType, errorMessage);
        reviewTaskService.markTaskFailedPermanently(taskId, errorMessage);
        return;
    }

    // Retryable error: update DB state
    ReviewTaskDTO updated = reviewTaskService.markTaskFailed(taskId, errorMessage);

    // If task reverted to PENDING (still has retries left), requeue with delay
    if (updated.getStatus() == TaskStatus.PENDING) {
        int delay = calculateRetryDelaySeconds(updated.getRetryCount() - 1); // -1 because retryCount was just incremented
        try {
            queueService.requeueWithDelay(taskId, updated.getPriority(), delay);
            log.info("Requeued task {} with {}s delay (attempt {}/{})",
                    taskId, delay, updated.getRetryCount(), updated.getMaxRetries());
        } catch (Exception e) {
            log.error("Failed to requeue task {} to Redis. DB state saved but not queued.", taskId, e);
        }
    } else {
        // Max retries exhausted → FAILED
        log.warn("Task {} permanently failed after {} retries: {}", taskId, updated.getRetryCount(), errorMessage);
    }
}
```

**markTaskFailedPermanently() pattern:**
```java
public ReviewTaskDTO markTaskFailedPermanently(Long id, String errorMessage) {
    ReviewTask task = reviewTaskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ReviewTask", "id", id));

    // Only RUNNING tasks can be failed (same validation as markTaskFailed)
    if (task.getStatus() != TaskStatus.RUNNING) {
        throw new IllegalStateException("Cannot fail task " + id + ": expected RUNNING, was " + task.getStatus());
    }

    task.setStatus(TaskStatus.FAILED);
    task.setCompletedAt(Instant.now());
    task.setErrorMessage(errorMessage);
    // Do NOT increment retryCount - this wasn't a retry attempt

    ReviewTask updated = reviewTaskRepository.save(task);

    // Release queue lock (best-effort)
    try {
        queueService.releaseLock(id);
    } catch (Exception e) {
        log.error("Failed to release lock for permanently failed task {}", id, e);
    }

    return ReviewTaskMapper.toDTO(updated);
}
```

### Critical Patterns from Previous Stories (MUST follow)

1. **-parameters compiler flag NOT enabled**: Use `#p0` for SpEL, `@PathVariable("id")` with explicit value
2. **Redis operations are best-effort**: Wrap in try-catch inside `@Transactional` methods (Story 2.6 H1/H2 fix)
3. **Service returns DTOs**: Never entities (Story 2.5 pattern)
4. **Integration test cleanup**: Use `@BeforeEach` with repository/Redis cleanup, NOT `@Transactional` (RANDOM_PORT doesn't rollback)
5. **TaskPriority enum**: Uses `getPriorityScore()` (NOT `getScore()`)
6. **Logging pattern**: INFO for normal flow, WARN for retries/anomalies, ERROR for failures
7. **Polling loops for delay tests**: Use polling (200ms intervals, max wait) instead of `Thread.sleep()` (Story 2.6 M3 fix)
8. **Windows file lock**: Use `mvn compile test` (omit `clean` phase) if JAR is locked

### Test Patterns

**Unit Tests (Mock dependencies):**
```java
@ExtendWith(MockitoExtension.class)
class RetryServiceImplTest {
    @Mock ReviewTaskService reviewTaskService;
    @Mock QueueService queueService;
    @InjectMocks RetryServiceImpl retryService;

    @Test
    void handleTaskFailure_retryable_requeueWithDelay() {
        // Given: RATE_LIMIT failure, task has retries left
        when(reviewTaskService.markTaskFailed(1L, "429 Too Many Requests"))
                .thenReturn(ReviewTaskDTO.builder().id(1L).status(TaskStatus.PENDING)
                        .priority(TaskPriority.HIGH).retryCount(1).maxRetries(3).build());

        // When
        retryService.handleTaskFailure(1L, "429 Too Many Requests", FailureType.RATE_LIMIT);

        // Then
        verify(reviewTaskService).markTaskFailed(1L, "429 Too Many Requests");
        verify(queueService).requeueWithDelay(eq(1L), eq(TaskPriority.HIGH), anyInt());
        verify(reviewTaskService, never()).markTaskFailedPermanently(anyLong(), anyString());
    }

    @Test
    void handleTaskFailure_nonRetryable_permanentFail() {
        // Given: VALIDATION_ERROR
        // When
        retryService.handleTaskFailure(1L, "Invalid payload", FailureType.VALIDATION_ERROR);

        // Then
        verify(reviewTaskService).markTaskFailedPermanently(1L, "Invalid payload");
        verify(reviewTaskService, never()).markTaskFailed(anyLong(), anyString());
        verify(queueService, never()).requeueWithDelay(anyLong(), any(), anyInt());
    }
}
```

**Integration Tests (Real Redis + DB):**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RetryIntegrationTest {
    @Autowired RetryService retryService;
    @Autowired ReviewTaskService reviewTaskService;
    @Autowired QueueService queueService;
    @Autowired ReviewTaskRepository reviewTaskRepository;
    @Autowired RedisTemplate<String, Object> redisTemplate;

    // Test full retry lifecycle with real infrastructure
}
```

### Dependencies

**No new Maven dependencies required**:
- `ThreadLocalRandom` (java.util.concurrent) for jitter — already in JDK
- All Redis operations via existing `spring-boot-starter-data-redis`
- All DB operations via existing `spring-boot-starter-data-jpa`

### Modification Impact Analysis

**Breaking change**: `markTaskFailed()` will no longer re-enqueue to Redis automatically. Any direct callers of `markTaskFailed()` must be updated to use `RetryService.handleTaskFailure()` instead.

**Current callers of markTaskFailed()**:
- Unit tests (`ReviewTaskServiceImplTest`) — need to remove `verify(queueService).enqueue()` assertions
- No production code calls it directly yet (worker doesn't exist)
- The 2 resilience tests (Redis failure in markTaskFailed) — need to be updated or moved

**Backward compatibility**: Since no production code calls `markTaskFailed()` directly yet (the worker will be implemented in future stories), this change is safe. The tests need updating but no runtime behavior changes.

### References

- [Source: architecture.md - Retry Strategy Implementation - Retry Rules]
- [Source: architecture.md - Worker Module Structure - processor/RetryService.java]
- [Source: architecture.md - Process Patterns - Error Handling Chain]
- [Source: architecture.md - AI Degradation Strategy - Transient Errors Retry Table]
- [Source: epics.md - Story 2.7: 实现任务重试机制]
- [Source: epics.md - FR 1.2: 任务管理 - 重试机制]
- [Source: epics.md - NFR 2: 可靠性要求 - 任务重试机制]
- [Source: 2-6-redis-priority-queue-management.md - RedisQueueService.requeueWithDelay() implementation]
- [Source: 2-6-redis-priority-queue-management.md - Senior Developer Review - Best-effort Redis pattern]
- [Source: 2-5-code-review-task-creation-persistence.md - ReviewTaskServiceImpl.markTaskFailed() implementation]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- **DataIntegrityViolation Fix #1**: `Project.builder().platform("GITHUB")` → `.gitPlatform("GITHUB")` — field name mismatch
- **DataIntegrityViolation Fix #2**: `findAll().stream().filter()` → `findByRepoUrl()` — proper duplicate detection
- **DataIntegrityViolation Fix #3**: Missing `webhookSecret` (NOT NULL column) — added `.webhookSecret("test-retry-secret")` to project builder

### Completion Notes List

- All 8 ACs implemented and verified
- 20 unit tests in RetryServiceImplTest (error classification, delay calculation, retry/non-retry flows, Redis resilience, input validation, lock release)
- 5 integration tests in RetryIntegrationTest (full lifecycle, non-retryable, max retries, auth error, unknown error)
- 6 unit tests in FailureTypeTest (size assertion, description, retryable classification, valueOf)
- 3 new tests added to ReviewTaskServiceImplTest for markTaskFailedPermanently()
- markTaskFailed() no longer re-enqueues to Redis — RetryService handles requeue via requeueWithDelay()
- Pre-existing ReviewTaskIntegrationTest failures (webhook auth issues) are unrelated to Story 2.7

### Senior Developer Review (AI)

**Reviewer**: Claude Opus 4.6 (adversarial)
**Date**: 2026-02-14

**Issues Found**: 2 High, 2 Medium, 2 Low — all HIGH/MEDIUM fixed

| ID | Severity | Issue | Fix |
|----|----------|-------|-----|
| H1 | HIGH | `calculateRetryDelaySeconds` jitter 99.8% probability of no effect | Changed to `nextInt(0, 2)` for ~50% jitter |
| H2 | HIGH | Orphaned Redis lock when max retries exhausted (no `releaseLock()`) | Added `releaseLock()` in else branch + test |
| M1 | MEDIUM | Missing `FailureTypeTest` in common module | Created FailureTypeTest with 6 tests |
| M2 | MEDIUM | Null `failureType` parameter causes NPE | Added `Objects.requireNonNull()` + test |
| L1 | LOW | Duplicate `isRetryable` between RetryService and FailureType | Accepted — provides service-level abstraction |
| L2 | LOW | Story number in production code comment | Fixed — removed "(Story 2.7)" reference |

### File List

**New Files:**
- `backend/ai-code-review-common/src/main/java/com/aicodereview/common/enums/FailureType.java`
- `backend/ai-code-review-common/src/test/java/com/aicodereview/common/enums/FailureTypeTest.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/RetryService.java`
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/RetryServiceImpl.java`
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/RetryServiceImplTest.java`
- `backend/ai-code-review-api/src/test/java/com/aicodereview/api/RetryIntegrationTest.java`

**Modified Files:**
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewTaskService.java` — Added markTaskFailedPermanently()
- `backend/ai-code-review-service/src/main/java/com/aicodereview/service/impl/ReviewTaskServiceImpl.java` — Added markTaskFailedPermanently(), removed enqueue from markTaskFailed(), cleaned comment
- `backend/ai-code-review-service/src/test/java/com/aicodereview/service/impl/ReviewTaskServiceImplTest.java` — Updated markTaskFailed tests, added markTaskFailedPermanently tests

### Change Log

| Change | File | Description |
|--------|------|-------------|
| ADD | FailureType.java | Error classification enum with 6 types (4 retryable, 2 non-retryable) |
| ADD | FailureTypeTest.java | 6 unit tests for FailureType enum (size, descriptions, classification) |
| ADD | RetryService.java | Interface: handleTaskFailure(), calculateRetryDelaySeconds(), isRetryable() |
| ADD | RetryServiceImpl.java | Orchestrates retry: retryable → markTaskFailed + requeueWithDelay; non-retryable → markTaskFailedPermanently |
| MOD | ReviewTaskService.java | Added markTaskFailedPermanently() method signature |
| MOD | ReviewTaskServiceImpl.java | Removed enqueue from markTaskFailed(), added markTaskFailedPermanently() with lock release |
| ADD | RetryServiceImplTest.java | 20 unit tests covering all retry scenarios + input validation + lock release |
| MOD | ReviewTaskServiceImplTest.java | Updated markTaskFailed tests (no enqueue), added 3 markTaskFailedPermanently tests |
| ADD | RetryIntegrationTest.java | 5 integration tests with real Redis + PostgreSQL |

