package com.aicodereview.integration.im;

/**
 * Service for sending notifications via DingTalk custom robot webhook.
 *
 * @since 7.3.0
 */
public interface DingTalkWebhookService {

    /**
     * Send a Markdown notification to DingTalk.
     *
     * @param webhookUrl    the full webhook URL (including access_token)
     * @param secret        optional signing secret; null or blank to skip signing
     * @param markdownTitle title for the Markdown message
     * @param markdownText  body text in Markdown format
     * @return true if sent successfully (HTTP 200 + errcode=0), false otherwise
     */
    boolean sendNotification(String webhookUrl, String secret, String markdownTitle, String markdownText);
}
