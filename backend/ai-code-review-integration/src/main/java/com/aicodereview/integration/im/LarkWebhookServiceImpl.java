package com.aicodereview.integration.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of Lark (Feishu) webhook notification service.
 * <p>
 * Sends interactive card messages via Lark custom robot webhook API.
 * </p>
 *
 * @since 7.3.0
 */
@Slf4j
@Service
public class LarkWebhookServiceImpl implements LarkWebhookService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LarkWebhookServiceImpl(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean sendNotification(String webhookUrl, String title, String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Lark webhook URL is blank, skipping notification");
            return false;
        }

        try {
            String body = buildRequestBody(title, content);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String truncatedBody = truncateBody(response.body());
                log.warn("Lark webhook returned HTTP {}: {}", response.statusCode(), truncatedBody);
                return false;
            }

            // Check code in response
            JsonNode root = objectMapper.readTree(response.body());
            int code = root.has("code") ? root.get("code").asInt(-1) : -1;
            if (code != 0) {
                String msg = root.has("msg") ? root.get("msg").asText() : "unknown";
                log.warn("Lark webhook returned code={}, msg={}", code, msg);
                return false;
            }

            log.info("Lark notification sent successfully");
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lark webhook request interrupted: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to send Lark notification: {}", e.getMessage());
            return false;
        }
    }

    /** Package-private for unit testing. */
    String buildRequestBody(String title, String content) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("msg_type", "interactive");

        var card = root.putObject("card");

        // Header
        var header = card.putObject("header");
        var titleNode = header.putObject("title");
        titleNode.put("tag", "plain_text");
        titleNode.put("content", title);
        header.put("template", "red");

        // Elements - single Markdown element
        var elements = card.putArray("elements");
        var markdownElement = elements.addObject();
        markdownElement.put("tag", "markdown");
        markdownElement.put("content", content);

        return objectMapper.writeValueAsString(root);
    }

    private String truncateBody(String body) {
        if (body != null && body.length() > 200) {
            return body.substring(0, 200) + "...";
        }
        return body;
    }
}
