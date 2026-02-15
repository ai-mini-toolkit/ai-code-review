-- V8__create_review_result_table.sql
-- Create review_result table for Story 5.1: Review Result Persistence Storage
--
-- This table stores AI code review results with JSONB columns for flexible
-- issue lists, statistics, and execution metadata. Each result has a 1:1
-- relationship with a review_task.

CREATE TABLE IF NOT EXISTS review_result (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL UNIQUE,
    issues JSONB NOT NULL DEFAULT '[]'::jsonb,
    statistics JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_result_task FOREIGN KEY (task_id) REFERENCES review_task(id) ON DELETE CASCADE
);

-- Index for performance (task_id index is implicitly created by UNIQUE constraint)
CREATE INDEX idx_review_result_created_at ON review_result(created_at DESC);

-- Table and column comments for documentation
COMMENT ON TABLE review_result IS 'AI code review results with JSONB storage for issues, statistics, and metadata';
COMMENT ON COLUMN review_result.id IS 'Primary key';
COMMENT ON COLUMN review_result.task_id IS 'Foreign key to review_task table (1:1, cascade delete)';
COMMENT ON COLUMN review_result.issues IS 'JSONB array of ReviewIssue objects from AI analysis';
COMMENT ON COLUMN review_result.statistics IS 'JSONB object with aggregated counts by severity and category';
COMMENT ON COLUMN review_result.metadata IS 'JSONB object with review execution metadata (provider, model, tokens, duration)';
COMMENT ON COLUMN review_result.success IS 'Whether the AI review completed successfully';
COMMENT ON COLUMN review_result.error_message IS 'Error message if review failed (nullable)';
COMMENT ON COLUMN review_result.created_at IS 'Result creation timestamp';
COMMENT ON COLUMN review_result.updated_at IS 'Last update timestamp';
