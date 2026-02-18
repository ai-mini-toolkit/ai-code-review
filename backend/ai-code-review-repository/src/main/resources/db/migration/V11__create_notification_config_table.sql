-- V11: Create notification_config table for email notification settings
-- Story 7.1: Email Notification Service

CREATE TABLE notification_config (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE REFERENCES project(id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT false,
    email_recipients VARCHAR(1000),
    smtp_host VARCHAR(255),
    smtp_port INTEGER,
    smtp_username VARCHAR(255),
    smtp_password VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_config_project_id ON notification_config(project_id);
