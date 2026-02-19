-- V15: Add IM webhook fields to notification_config table
-- Story 7.3: IM Webhook Notifications (DingTalk, Slack, Lark)

ALTER TABLE notification_config
    ADD COLUMN dingtalk_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN dingtalk_webhook_url VARCHAR(500),
    ADD COLUMN dingtalk_secret VARCHAR(500),
    ADD COLUMN slack_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN slack_webhook_url VARCHAR(500),
    ADD COLUMN lark_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN lark_webhook_url VARCHAR(500);
