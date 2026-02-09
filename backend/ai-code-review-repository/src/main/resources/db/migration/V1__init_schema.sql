-- V1__init_schema.sql
-- Initial database schema for AI Code Review System
-- Created for Story 1.3: Configure PostgreSQL & JPA

-- Create test table to verify Flyway migration
CREATE TABLE IF NOT EXISTS system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value TEXT,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on config_key for faster lookups
CREATE INDEX idx_system_config_key ON system_config(config_key);

-- Insert initial test data
INSERT INTO system_config (config_key, config_value, description)
VALUES ('system.version', '1.0.0', 'Current system version');

INSERT INTO system_config (config_key, config_value, description)
VALUES ('database.migration.status', 'initialized', 'Database migration status');

-- Add comments
COMMENT ON TABLE system_config IS 'System configuration key-value store';
COMMENT ON COLUMN system_config.config_key IS 'Unique configuration key';
COMMENT ON COLUMN system_config.config_value IS 'Configuration value';
