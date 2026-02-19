package com.aicodereview.integration.im;

/**
 * Service for sending notifications via Slack Incoming Webhook.
 *
 * @since 7.3.0
 */
public interface SlackWebhookService {

    /**
     * Send a text notification to Slack.
     *
     * @param webhookUrl   the Slack Incoming Webhook URL
     * @param markdownText body text in Slack mrkdwn format
     * @return true if sent successfully (HTTP 200), false otherwise
     */
    boolean sendNotification(String webhookUrl, String markdownText);
}
