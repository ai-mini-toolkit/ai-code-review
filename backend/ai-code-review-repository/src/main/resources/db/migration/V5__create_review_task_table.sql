-- V5__create_review_task_table.sql
-- Create review_task table for Story 2.5: Code Review Task Creation & Persistence
--
-- This table stores code review tasks created from webhook events (push, pull_request, merge_request).
-- Tasks track the complete lifecycle: PENDING → RUNNING → COMPLETED/FAILED

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

-- Indexes for performance
CREATE INDEX idx_review_task_project_id ON review_task(project_id);
CREATE INDEX idx_review_task_status ON review_task(status);
CREATE INDEX idx_review_task_priority ON review_task(priority);
CREATE INDEX idx_review_task_created_at ON review_task(created_at);

-- Composite index for queue operations (optimized for priority queue queries)
-- Used by Story 2.6 Redis queue to fetch tasks in priority order
CREATE INDEX idx_review_task_status_priority_created ON review_task(status, priority DESC, created_at ASC);

-- Table and column comments for documentation
COMMENT ON TABLE review_task IS 'Code review tasks created from webhook events (GitHub, GitLab, AWS CodeCommit)';
COMMENT ON COLUMN review_task.id IS 'Primary key';
COMMENT ON COLUMN review_task.project_id IS 'Foreign key to project table';
COMMENT ON COLUMN review_task.task_type IS 'Task type: PUSH (code push), PULL_REQUEST (GitHub PR), or MERGE_REQUEST (GitLab MR)';
COMMENT ON COLUMN review_task.repo_url IS 'Git repository URL from webhook event';
COMMENT ON COLUMN review_task.branch IS 'Branch name (e.g., main, feature/auth)';
COMMENT ON COLUMN review_task.commit_hash IS 'Git commit SHA hash (40 character hex string)';
COMMENT ON COLUMN review_task.pr_number IS 'Pull Request or Merge Request number (nullable for PUSH tasks)';
COMMENT ON COLUMN review_task.pr_title IS 'PR/MR title from webhook';
COMMENT ON COLUMN review_task.pr_description IS 'PR/MR description/body from webhook';
COMMENT ON COLUMN review_task.author IS 'Commit author or PR/MR creator';
COMMENT ON COLUMN review_task.status IS 'Task status: PENDING (queued), RUNNING (executing), COMPLETED (success), or FAILED (error)';
COMMENT ON COLUMN review_task.priority IS 'Task priority: HIGH (PR/MR - immediate review), NORMAL (PUSH - batch review)';
COMMENT ON COLUMN review_task.retry_count IS 'Number of retry attempts made (incremented on failure)';
COMMENT ON COLUMN review_task.max_retries IS 'Maximum number of retries allowed (default 3)';
COMMENT ON COLUMN review_task.error_message IS 'Error message if task failed (used for debugging and retry logic)';
COMMENT ON COLUMN review_task.created_at IS 'Task creation timestamp (when webhook received)';
COMMENT ON COLUMN review_task.started_at IS 'Task execution start timestamp (when worker picks up task)';
COMMENT ON COLUMN review_task.completed_at IS 'Task completion timestamp (success or final failure)';
COMMENT ON COLUMN review_task.updated_at IS 'Last update timestamp (auto-updated by JPA auditing)';
