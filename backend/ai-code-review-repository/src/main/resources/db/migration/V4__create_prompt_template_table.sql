-- V4__create_prompt_template_table.sql
-- Create prompt template table for Story 1.7: Prompt Template Management API

CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL CONSTRAINT chk_prompt_template_category CHECK (category IN ('security', 'performance', 'maintainability', 'correctness', 'style', 'best_practices')),
    template_content TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for frequently queried columns
-- Note: name column already has a unique index from UNIQUE constraint, no need for additional index
CREATE INDEX idx_prompt_template_category ON prompt_template(category);
CREATE INDEX idx_prompt_template_enabled ON prompt_template(enabled);

-- Table and column comments
COMMENT ON TABLE prompt_template IS 'Prompt templates for six-dimension AI code review';
COMMENT ON COLUMN prompt_template.id IS 'Primary key';
COMMENT ON COLUMN prompt_template.name IS 'Unique template name';
COMMENT ON COLUMN prompt_template.category IS 'Review dimension: security, performance, maintainability, correctness, style, best_practices';
COMMENT ON COLUMN prompt_template.template_content IS 'Mustache template content for AI prompt generation';
COMMENT ON COLUMN prompt_template.version IS 'Template version number for tracking iterations';
COMMENT ON COLUMN prompt_template.enabled IS 'Whether this template is active';
COMMENT ON COLUMN prompt_template.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN prompt_template.updated_at IS 'Record last update timestamp';
