package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ErrorCode;
import com.aicodereview.integration.webhook.WebhookVerificationChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for WebhookController
 */
@WebMvcTest(WebhookController.class)
@DisplayName("WebhookController Unit Tests")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookVerificationChain verificationChain;

    @Test
    @DisplayName("POST /api/webhook/github - valid signature should return 202 Accepted")
    void testReceiveWebhook_GitHub_ValidSignature_Returns202() throws Exception {
        // Given: Valid GitHub webhook with valid signature
        String payload = "{\"ref\":\"refs/heads/main\",\"repository\":{\"name\":\"test-repo\",\"full_name\":\"user/test-repo\"},\"pusher\":{\"name\":\"testuser\"}}";
        String signature = "sha256=valid-signature";

        // Mock verification success
        when(verificationChain.verify(eq("github"), eq(payload), eq(signature), anyString()))
                .thenReturn(true);

        // When & Then: Should return 202 Accepted
        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Hub-Signature-256", signature))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("POST /api/webhook/gitlab - valid token should return 202 Accepted")
    void testReceiveWebhook_GitLab_ValidToken_Returns202() throws Exception {
        // Given: Valid GitLab webhook with valid token
        String payload = "{\"object_kind\":\"push\",\"project\":{\"name\":\"test-project\",\"path_with_namespace\":\"user/test-project\"},\"user_username\":\"testuser\"}";
        String token = "test-gitlab-token";

        // Mock verification success
        when(verificationChain.verify(eq("gitlab"), eq(payload), eq(token), anyString()))
                .thenReturn(true);

        // When & Then: Should return 202 Accepted
        mockMvc.perform(post("/api/webhook/gitlab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Gitlab-Token", token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/webhook/github - invalid signature should return 401 Unauthorized")
    void testReceiveWebhook_InvalidSignature_Returns401() throws Exception {
        // Given: Webhook with invalid signature
        String payload = "{\"ref\":\"refs/heads/main\"}";
        String invalidSignature = "sha256=invalid";

        // Mock verification failure
        when(verificationChain.verify(eq("github"), eq(payload), eq(invalidSignature), anyString()))
                .thenReturn(false);

        // When & Then: Should return 401 Unauthorized
        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Hub-Signature-256", invalidSignature))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ERR_401"));
    }

    @Test
    @DisplayName("POST /api/webhook/github - missing signature header should return 401")
    void testReceiveWebhook_MissingSignatureHeader_Returns401() throws Exception {
        // Given: Webhook without signature header
        String payload = "{\"ref\":\"refs/heads/main\"}";

        // When & Then: Should return 401 (signature header missing)
        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/webhook/github - malformed JSON should return 422")
    void testReceiveWebhook_MalformedJSON_Returns422() throws Exception {
        // Given: Malformed JSON payload
        String malformedPayload = "{invalid json";
        String signature = "sha256=test";

        // Mock verification success (signature check passes first)
        when(verificationChain.verify(eq("github"), eq(malformedPayload), eq(signature), anyString()))
                .thenReturn(true);

        // When & Then: Should return 422 Unprocessable Entity (handled by GlobalExceptionHandler)
        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedPayload)
                        .header("X-Hub-Signature-256", signature))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ERR_422"));
    }

    @Test
    @DisplayName("POST /api/webhook/github - missing required fields should return 422")
    void testReceiveWebhook_MissingRequiredFields_Returns422() throws Exception {
        // Given: Valid JSON but missing required fields
        String payload = "{\"ref\":\"refs/heads/main\"}"; // Missing repository and pusher
        String signature = "sha256=test";

        // Mock verification success
        when(verificationChain.verify(eq("github"), eq(payload), eq(signature), anyString()))
                .thenReturn(true);

        // When & Then: Should return 422 (missing required fields)
        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Hub-Signature-256", signature))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/webhook/unknown - unknown platform should return 400")
    void testReceiveWebhook_UnknownPlatform_Returns400() throws Exception {
        // Given: Webhook for unknown platform
        String payload = "{\"test\":\"data\"}";

        // When & Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/webhook/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Custom-Header", "test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/webhook/codecommit - SNS message should be handled")
    void testReceiveWebhook_CodeCommit_SNSMessage() throws Exception {
        // Given: AWS CodeCommit SNS notification
        String snsPayload = "{\"Type\":\"Notification\",\"MessageId\":\"test-id\",\"SigningCertURL\":\"https://sns.us-east-1.amazonaws.com/cert.pem\",\"Message\":\"{}\"}";
        String signature = "dummy"; // SNS signature embedded in payload

        // Mock verification (will fail for now as AWS verification not fully implemented)
        when(verificationChain.verify(eq("codecommit"), eq(snsPayload), anyString(), anyString()))
                .thenReturn(false);

        // When & Then: Should return 401 (AWS verification not ready)
        mockMvc.perform(post("/api/webhook/codecommit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(snsPayload)
                        .header("X-Custom", signature))
                .andExpect(status().isUnauthorized());
    }
}
