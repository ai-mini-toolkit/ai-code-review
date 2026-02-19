package com.aicodereview.integration.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Implementation of DingTalk webhook notification service.
 * <p>
 * Sends Markdown messages via DingTalk custom robot webhook API.
 * Supports optional HMAC-SHA256 signing for secure communication.
 * </p>
 *
 * @since 7.3.0
 */
@Slf4j
@Service
public class DingTalkWebhookServiceImpl implements DingTalkWebhookService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DingTalkWebhookServiceImpl(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean sendNotification(String webhookUrl, String secret, String markdownTitle, String markdownText) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("DingTalk webhook URL is blank, skipping notification");
            return false;
        }

        try {
            String url = appendSignature(webhookUrl, secret);
            String body = buildRequestBody(markdownTitle, markdownText);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String truncatedBody = truncateBody(response.body());
                log.warn("DingTalk webhook returned HTTP {}: {}", response.statusCode(), truncatedBody);
                return false;
            }

            // Check errcode in response
            JsonNode root = objectMapper.readTree(response.body());
            int errcode = root.has("errcode") ? root.get("errcode").asInt(-1) : -1;
            if (errcode != 0) {
                String errmsg = root.has("errmsg") ? root.get("errmsg").asText() : "unknown";
                log.warn("DingTalk webhook returned errcode={}, errmsg={}", errcode, errmsg);
                return false;
            }

            log.info("DingTalk notification sent successfully");
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("DingTalk webhook request interrupted: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to send DingTalk notification: {}", e.getMessage());
            return false;
        }
    }

    /** Package-private for unit testing. */
    String appendSignature(String webhookUrl, String secret) throws Exception {
        if (secret == null || secret.isBlank()) {
            return webhookUrl;
        }

        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(
                Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);

        String separator = webhookUrl.contains("?") ? "&" : "?";
        return webhookUrl + separator + "timestamp=" + timestamp + "&sign=" + sign;
    }

    /** Package-private for unit testing. */
    String buildRequestBody(String title, String text) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("msgtype", "markdown");
        var markdown = root.putObject("markdown");
        markdown.put("title", title);
        markdown.put("text", text);
        return objectMapper.writeValueAsString(root);
    }

    private String truncateBody(String body) {
        if (body != null && body.length() > 200) {
            return body.substring(0, 200) + "...";
        }
        return body;
    }
}
