-- V9: Add thresholds JSONB column to project table for quality gate configuration
-- Story 6.1: Threshold Configuration Management

ALTER TABLE project
ADD COLUMN thresholds JSONB NOT NULL DEFAULT '{
  "enabled": false,
  "rules": [
    {"severity": "CRITICAL", "maxCount": 0},
    {"severity": "HIGH", "maxCount": 5},
    {"totalIssues": 30}
  ],
  "action": "BLOCK_MERGE"
}'::jsonb;

CREATE INDEX idx_project_thresholds ON project USING GIN (thresholds);
