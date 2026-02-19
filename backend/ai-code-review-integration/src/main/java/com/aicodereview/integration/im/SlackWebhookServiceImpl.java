package com.aicodereview.integration.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of Slack webhook notification service.
 * <p>
 * Sends text messages via Slack Incoming Webhook API.
 * Uses Slack mrkdwn format for message formatting.
 * </p>
 *
 * @since 7.3.0
 */
@Slf4j
@Service
public class SlackWebhookServiceImpl implements SlackWebhookService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SlackWebhookServiceImpl(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean sendNotification(String webhookUrl, String markdownText) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Slack webhook URL is blank, skipping notification");
            return false;
        }

        try {
            String body = buildRequestBody(markdownText);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String truncatedBody = truncateBody(response.body());
                log.warn("Slack webhook returned HTTP {}: {}", response.statusCode(), truncatedBody);
                return false;
            }

            log.info("Slack notification sent successfully");
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Slack webhook request interrupted: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to send Slack notification: {}", e.getMessage());
            return false;
        }
    }

    String buildRequestBody(String text) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("text", text);
        return objectMapper.writeValueAsString(root);
    }

    private String truncateBody(String body) {
        if (body != null && body.length() > 200) {
            return body.substring(0, 200) + "...";
        }
        return body;
    }
}
