-- V7: Add code_context column to review_task table
-- Stores serialized CodeContext JSON for AI review consumption

ALTER TABLE review_task ADD COLUMN code_context TEXT;

COMMENT ON COLUMN review_task.code_context IS 'Serialized CodeContext JSON for AI review';
