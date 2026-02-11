# Review Task Table

## Overview

The `review_task` table stores code review tasks created from webhook events (GitHub, GitLab, AWS CodeCommit). Each task represents a code change that needs to be reviewed by the AI system.

## Table Structure

### Database Schema

**Created by**: Flyway migration `V5__create_review_task_table.sql`
**Story**: 2.5 - Code Review Task Creation & Persistence

```sql
CREATE TABLE IF NOT EXISTS review_task (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    task_type VARCHAR(20) NOT NULL CHECK (task_type IN ('PUSH', 'PULL_REQUEST', 'MERGE_REQUEST')),
    repo_url VARCHAR(500) NOT NULL,
    branch VARCHAR(255) NOT NULL,
    commit_hash VARCHAR(255) NOT NULL,
    pr_number INTEGER,
    pr_title TEXT,
    pr_description TEXT,
    author VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('HIGH', 'NORMAL')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_task_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);
```

## Field Descriptions

### Identity & Associations
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGSERIAL | Primary key (auto-generated) |
| `project_id` | BIGINT | Foreign key to `project` table (NOT NULL) |

### Task Metadata
| Field | Type | Description |
|-------|------|-------------|
| `task_type` | VARCHAR(20) | Task source type: `PUSH`, `PULL_REQUEST`, `MERGE_REQUEST` |
| `repo_url` | VARCHAR(500) | Git repository URL from webhook |
| `branch` | VARCHAR(255) | Branch name (e.g., `main`, `feature/auth`) |
| `commit_hash` | VARCHAR(255) | Git commit SHA hash (40-character hex) |
| `author` | VARCHAR(255) | Commit author or PR/MR creator |

### Pull Request / Merge Request Fields (Optional)
| Field | Type | Description |
|-------|------|-------------|
| `pr_number` | INTEGER | PR/MR number (nullable for PUSH tasks) |
| `pr_title` | TEXT | PR/MR title (nullable for PUSH tasks) |
| `pr_description` | TEXT | PR/MR description/body (nullable for PUSH tasks) |

### Task Status & Execution
| Field | Type | Description |
|-------|------|-------------|
| `status` | VARCHAR(20) | Task lifecycle status (see [Task Lifecycle](#task-lifecycle)) |
| `priority` | VARCHAR(20) | Queue priority: `HIGH` (PR/MR), `NORMAL` (PUSH) |
| `retry_count` | INTEGER | Number of retry attempts made (default: 0) |
| `max_retries` | INTEGER | Maximum retries allowed (default: 3) |
| `error_message` | TEXT | Error description if failed (nullable) |

### Timestamps
| Field | Type | Description |
|-------|------|-------------|
| `created_at` | TIMESTAMP | Task creation time (auto-set, immutable) |
| `started_at` | TIMESTAMP | Execution start time (set when worker picks up task) |
| `completed_at` | TIMESTAMP | Completion time (success or final failure) |
| `updated_at` | TIMESTAMP | Last modification time (auto-updated) |

## Task Lifecycle

```
┌─────────┐      markTaskStarted()      ┌─────────┐
│ PENDING ├─────────────────────────────>│ RUNNING │
└────┬────┘                              └────┬────┘
     │                                        │
     │ markTaskFailed()                      │ markTaskCompleted()
     │ (retry_count < max_retries)            │
     │                                        │
     └────────────────────┐    ┌─────────────┘
                          │    │
                          ▼    ▼
                    ┌──────────────┐
                    │  COMPLETED   │
                    └──────────────┘

                    ┌──────────────┐
                    │   FAILED     │ ◄─── markTaskFailed() (retry_count >= max_retries)
                    └──────────────┘
```

### Status Transitions

1. **PENDING** (initial state)
   - Task created and waiting for worker
   - Can transition to RUNNING when worker picks it up

2. **RUNNING** (active execution)
   - Worker is processing the task
   - Can transition to COMPLETED (success) or PENDING (retry) or FAILED (max retries)

3. **COMPLETED** (terminal state)
   - Task executed successfully
   - `completed_at` timestamp set

4. **FAILED** (terminal state)
   - Task failed after max retries exhausted
   - `completed_at` timestamp set
   - `error_message` contains failure reason

## Indexes

Performance-optimized indexes for queue operations:

```sql
-- Single column indexes
CREATE INDEX idx_review_task_project_id ON review_task(project_id);
CREATE INDEX idx_review_task_status ON review_task(status);
CREATE INDEX idx_review_task_priority ON review_task(priority);
CREATE INDEX idx_review_task_created_at ON review_task(created_at);

-- Composite index for queue operations (CRITICAL for performance)
CREATE INDEX idx_review_task_status_priority_created ON review_task(status, priority DESC, created_at ASC);
```

### Composite Index Usage

The composite index `idx_review_task_status_priority_created` is used by:

```java
List<ReviewTask> findByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus status);
```

This query powers the Redis queue worker to fetch tasks in priority order:
- HIGH priority tasks first (PR/MR)
- Within same priority, oldest tasks first (FIFO)

**Query Example**:
```sql
SELECT * FROM review_task
WHERE status = 'PENDING'
ORDER BY priority DESC, created_at ASC
LIMIT 10;
```

## Priority Scores

Task priority determines queue processing order:

| Priority | Score | Used For | Rationale |
|----------|-------|----------|-----------|
| `HIGH` | 100 | Pull Requests, Merge Requests | Blocks developer workflow; needs immediate review |
| `NORMAL` | 50 | Push events | Can be batch-processed; less time-sensitive |

## Retry Logic

Tasks can fail due to transient errors (network, AI API rate limits, etc.):

1. **First Failure**: `retry_count = 1`, status → PENDING (task re-queued)
2. **Second Failure**: `retry_count = 2`, status → PENDING
3. **Third Failure**: `retry_count = 3`, status → PENDING
4. **Fourth Failure**: `retry_count = 4` (>= max_retries), status → FAILED (terminal)

**Implementation**: See `ReviewTaskServiceImpl.markTaskFailed()`

## JPA Entity

**Class**: `com.aicodereview.repository.entity.ReviewTask`
**Location**: `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewTask.java`

Key annotations:
- `@Entity` - JPA entity
- `@Table(name = "review_task")` - Table mapping
- `@EntityListeners(AuditingEntityListener.class)` - Audit support
- `@CreatedDate` / `@LastModifiedDate` - Auto-timestamps
- `@ManyToOne(fetch = FetchType.LAZY)` - Lazy project loading

## Repository Interface

**Interface**: `com.aicodereview.repository.ReviewTaskRepository`
**Location**: `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/ReviewTaskRepository.java`

Key query methods:
```java
List<ReviewTask> findByProjectId(Long projectId);
List<ReviewTask> findByStatus(TaskStatus status);
List<ReviewTask> findByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus status); // Queue fetch
Optional<ReviewTask> findByProjectIdAndCommitHash(Long projectId, String commitHash); // Duplicate check
```

## Service Layer

**Service**: `com.aicodereview.service.ReviewTaskService`
**Implementation**: `com.aicodereview.service.impl.ReviewTaskServiceImpl`

Business logic methods:
```java
ReviewTaskDTO createTask(CreateReviewTaskRequest request);
ReviewTaskDTO markTaskStarted(Long id);
ReviewTaskDTO markTaskCompleted(Long id);
ReviewTaskDTO markTaskFailed(Long id, String errorMessage);
boolean canRetry(Long id);
```

## WebhookController Integration

When a webhook is received:

1. WebhookController verifies signature
2. Extracts webhook fields (repoUrl, branch, commitHash, author, PR info)
3. Finds project by repoUrl
4. Calls `reviewTaskService.createTask(request)`
5. Task saved to database with PENDING status
6. Returns 202 Accepted

**Flow**: Webhook → WebhookController → ReviewTaskService → ReviewTaskRepository → Database

## Example Task Record

### GitHub Pull Request Event

```json
{
  "id": 1,
  "project_id": 5,
  "task_type": "PULL_REQUEST",
  "repo_url": "https://github.com/org/repo",
  "branch": "feature/user-auth",
  "commit_hash": "a1b2c3d4e5f6789012345678901234567890abcd",
  "pr_number": 42,
  "pr_title": "Add user authentication",
  "pr_description": "Implements JWT-based authentication for API endpoints",
  "author": "john.doe",
  "status": "PENDING",
  "priority": "HIGH",
  "retry_count": 0,
  "max_retries": 3,
  "error_message": null,
  "created_at": "2026-02-11T10:30:00Z",
  "started_at": null,
  "completed_at": null,
  "updated_at": "2026-02-11T10:30:00Z"
}
```

### Push Event (No PR)

```json
{
  "id": 2,
  "project_id": 5,
  "task_type": "PUSH",
  "repo_url": "https://github.com/org/repo",
  "branch": "main",
  "commit_hash": "b2c3d4e5f6a789012345678901234567890abcde",
  "pr_number": null,
  "pr_title": null,
  "pr_description": null,
  "author": "jane.smith",
  "status": "PENDING",
  "priority": "NORMAL",
  "retry_count": 0,
  "max_retries": 3,
  "error_message": null,
  "created_at": "2026-02-11T10:35:00Z",
  "started_at": null,
  "completed_at": null,
  "updated_at": "2026-02-11T10:35:00Z"
}
```

## Related Files

- **Migration**: `backend/ai-code-review-repository/src/main/resources/db/migration/V5__create_review_task_table.sql`
- **Entity**: `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/ReviewTask.java`
- **Repository**: `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/ReviewTaskRepository.java`
- **Service**: `backend/ai-code-review-service/src/main/java/com/aicodereview/service/ReviewTaskService.java`
- **DTO**: `backend/ai-code-review-common/src/main/java/com/aicodereview/common/dto/reviewtask/ReviewTaskDTO.java`
- **Mapper**: `backend/ai-code-review-service/src/main/java/com/aicodereview/service/mapper/ReviewTaskMapper.java`

## Testing

### Unit Tests
- **ReviewTaskServiceImplTest**: 15 test cases (100% pass rate)
  - Task creation with priority assignment
  - Status transitions
  - Retry logic
  - Edge cases

### Integration Tests
- **ReviewTaskIntegrationTest**: End-to-end webhook → database flow
  - GitHub push/PR events
  - GitLab MR events
  - Task lifecycle transitions

## Future Enhancements

- **Story 2.6**: Redis priority queue integration
- **Story 2.7**: Advanced retry strategies (exponential backoff)
- **Epic 5**: Link tasks to review results

---

**Created**: 2026-02-11 (Story 2.5)
**Last Updated**: 2026-02-11
