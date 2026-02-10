-- V3__create_ai_model_config_table.sql
-- Create AI model configuration table for Story 1.6: AI Model Config Management API

CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL CONSTRAINT chk_ai_model_provider CHECK (provider IN ('openai', 'anthropic', 'custom')),
    model_name VARCHAR(100) NOT NULL,
    api_key VARCHAR(500) NOT NULL,
    api_endpoint VARCHAR(500),
    temperature DECIMAL(3,2) DEFAULT 0.3,
    max_tokens INT DEFAULT 4000,
    timeout_seconds INT DEFAULT 30,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for frequently queried columns
-- Note: name column already has a unique index from UNIQUE constraint, no need for additional index
CREATE INDEX idx_ai_model_config_provider ON ai_model_config(provider);
CREATE INDEX idx_ai_model_config_enabled ON ai_model_config(enabled);

-- Table and column comments
COMMENT ON TABLE ai_model_config IS 'AI model configuration for code review providers';
COMMENT ON COLUMN ai_model_config.id IS 'Primary key';
COMMENT ON COLUMN ai_model_config.name IS 'Unique model configuration name';
COMMENT ON COLUMN ai_model_config.provider IS 'AI provider type: openai, anthropic, or custom';
COMMENT ON COLUMN ai_model_config.model_name IS 'Model identifier (e.g., gpt-4, claude-opus)';
COMMENT ON COLUMN ai_model_config.api_key IS 'API key (AES-256-GCM encrypted)';
COMMENT ON COLUMN ai_model_config.api_endpoint IS 'API endpoint URL (e.g., https://api.openai.com/v1)';
COMMENT ON COLUMN ai_model_config.temperature IS 'Model temperature parameter (0.0-2.0)';
COMMENT ON COLUMN ai_model_config.max_tokens IS 'Maximum tokens per request';
COMMENT ON COLUMN ai_model_config.timeout_seconds IS 'API call timeout in seconds';
COMMENT ON COLUMN ai_model_config.enabled IS 'Whether this model configuration is active';
COMMENT ON COLUMN ai_model_config.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN ai_model_config.updated_at IS 'Record last update timestamp';
