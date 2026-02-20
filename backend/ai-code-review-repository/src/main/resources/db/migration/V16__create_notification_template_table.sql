-- V16__create_notification_template_table.sql
-- Create notification template table for Story 7.4: Notification Template Management

CREATE TABLE IF NOT EXISTS notification_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    channel VARCHAR(50) NOT NULL CONSTRAINT chk_notification_template_channel
        CHECK (channel IN ('EMAIL', 'GIT_COMMENT', 'DINGTALK', 'SLACK', 'LARK')),
    template_content TEXT NOT NULL,
    variables JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for frequently queried columns
CREATE INDEX idx_notification_template_channel ON notification_template(channel);
CREATE INDEX idx_notification_template_enabled ON notification_template(enabled);

-- Table and column comments
COMMENT ON TABLE notification_template IS 'Notification templates for multi-channel notifications (email, git comment, IM webhooks)';
COMMENT ON COLUMN notification_template.id IS 'Primary key';
COMMENT ON COLUMN notification_template.name IS 'Unique template name';
COMMENT ON COLUMN notification_template.channel IS 'Notification channel: EMAIL, GIT_COMMENT, DINGTALK, SLACK, LARK';
COMMENT ON COLUMN notification_template.template_content IS 'Mustache/Handlebars template content for notification rendering';
COMMENT ON COLUMN notification_template.variables IS 'JSONB documentation of available template variables and their descriptions';
COMMENT ON COLUMN notification_template.enabled IS 'Whether this template is active';
COMMENT ON COLUMN notification_template.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN notification_template.updated_at IS 'Record last update timestamp';
