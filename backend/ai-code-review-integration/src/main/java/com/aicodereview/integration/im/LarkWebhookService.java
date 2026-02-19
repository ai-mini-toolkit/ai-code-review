package com.aicodereview.integration.im;

/**
 * Service for sending notifications via Lark (Feishu) custom robot webhook.
 *
 * @since 7.3.0
 */
public interface LarkWebhookService {

    /**
     * Send an interactive card notification to Lark.
     *
     * @param webhookUrl the Lark webhook URL
     * @param title      card header title
     * @param content    card body content (Markdown-like format)
     * @return true if sent successfully (HTTP 200 + code=0), false otherwise
     */
    boolean sendNotification(String webhookUrl, String title, String content);
}
