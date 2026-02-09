-- V2__create_project_table.sql
-- Create project configuration table for Story 1.5: Project Config Management API

CREATE TABLE IF NOT EXISTS project (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    git_platform VARCHAR(50) NOT NULL,
    repo_url VARCHAR(500) NOT NULL,
    webhook_secret VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for frequently queried columns
CREATE INDEX idx_project_name ON project(name);
CREATE INDEX idx_project_enabled ON project(enabled);

-- Table and column comments
COMMENT ON TABLE project IS 'Project configuration for AI code review integration';
COMMENT ON COLUMN project.id IS 'Primary key';
COMMENT ON COLUMN project.name IS 'Unique project name';
COMMENT ON COLUMN project.description IS 'Project description';
COMMENT ON COLUMN project.enabled IS 'Whether code review is enabled for this project';
COMMENT ON COLUMN project.git_platform IS 'Git platform type: GitHub, GitLab, or CodeCommit';
COMMENT ON COLUMN project.repo_url IS 'Git repository URL';
COMMENT ON COLUMN project.webhook_secret IS 'Webhook secret key (AES encrypted)';
COMMENT ON COLUMN project.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN project.updated_at IS 'Record last update timestamp';
