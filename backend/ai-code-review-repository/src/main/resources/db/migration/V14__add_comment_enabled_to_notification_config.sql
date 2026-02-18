-- V14: Add comment_enabled column to notification_config table
-- Story 7.2: Git Platform Comment Notification

ALTER TABLE notification_config
    ADD COLUMN comment_enabled BOOLEAN NOT NULL DEFAULT false;
