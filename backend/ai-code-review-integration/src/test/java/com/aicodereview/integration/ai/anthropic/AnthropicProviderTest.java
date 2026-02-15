package com.aicodereview.integration.ai.anthropic;

import com.aicodereview.common.dto.review.ReviewIssue;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.FileInfo;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.common.enums.Language;
import com.aicodereview.common.exception.AIAuthenticationException;
import com.aicodereview.common.exception.AIProviderException;
import com.aicodereview.common.exception.AITimeoutException;
import com.aicodereview.common.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnthropicProviderTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private AnthropicProvider provider;

    private static final String API_KEY = "sk-ant-test-key";
    private static final String MODEL = "claude-sonnet-4-5-20250929";
    private static final int MAX_TOKENS = 4000;
    private static final double TEMPERATURE = 0.3;
    private static final int TIMEOUT_SECONDS = 60;
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @BeforeEach
    void setUp() {
        provider = new AnthropicProvider(
                httpClient, API_KEY, "", MODEL, MAX_TOKENS, TEMPERATURE, TIMEOUT_SECONDS, ANTHROPIC_VERSION);
    }

    // ========== Task 1 Tests: Provider Identity, isAvailable, getMaxTokens ==========

    @Nested
    @DisplayName("Provider Identity (AC1, AC8)")
    class ProviderIdentity {

        @Test
        @DisplayName("getProviderId returns 'anthropic'")
        void shouldReturnAnthropicProviderId() {
            assertThat(provider.getProviderId()).isEqualTo("anthropic");
        }

        @Test
        @DisplayName("isAvailable returns true when API key is configured")
        void shouldBeAvailableWithApiKey() {
            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("isAvailable returns false when API key is empty")
        void shouldNotBeAvailableWithEmptyApiKey() {
            AnthropicProvider emptyKeyProvider = new AnthropicProvider(
                    httpClient, "", "", MODEL, MAX_TOKENS, TEMPERATURE, TIMEOUT_SECONDS, ANTHROPIC_VERSION);
            assertThat(emptyKeyProvider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("isAvailable returns false when API key is null")
        void shouldNotBeAvailableWithNullApiKey() {
            AnthropicProvider nullKeyProvider = new AnthropicProvider(
                    httpClient, null, "", MODEL, MAX_TOKENS, TEMPERATURE, TIMEOUT_SECONDS, ANTHROPIC_VERSION);
            assertThat(nullKeyProvider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("isAvailable returns false when anthropicVersion is empty")
        void shouldNotBeAvailableWithEmptyVersion() {
            AnthropicProvider emptyVersionProvider = new AnthropicProvider(
                    httpClient, API_KEY, "", MODEL, MAX_TOKENS, TEMPERATURE, TIMEOUT_SECONDS, "");
            assertThat(emptyVersionProvider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("isAvailable returns false when anthropicVersion is null")
        void shouldNotBeAvailableWithNullVersion() {
            AnthropicProvider nullVersionProvider = new AnthropicProvider(
                    httpClient, API_KEY, "", MODEL, MAX_TOKENS, TEMPERATURE, TIMEOUT_SECONDS, null);
            assertThat(nullVersionProvider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("getMaxTokens returns configured value")
        void shouldReturnConfiguredMaxTokens() {
            assertThat(provider.getMaxTokens()).isEqualTo(MAX_TOKENS);
        }
    }

    // ========== Task 1 Tests: BaseUrl Resolution ==========

    @Nested
    @DisplayName("Base URL Resolution (AC2)")
    class BaseUrlResolution {

        @Test
        @DisplayName("Uses official Anthropic endpoint when apiEndpoint is empty")
        void shouldUseOfficialEndpointWhenEmpty() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            provider.analyze(buildMinimalContext(), "system prompt");

            verify(httpClient).send(argThat(request ->
                    request.uri().toString().equals("https://api.anthropic.com/v1/messages")
            ), any());
        }

        @Test
        @DisplayName("Uses official Anthropic endpoint when apiEndpoint is null")
        void shouldUseOfficialEndpointWhenNull() throws Exception {
            AnthropicProvider nullEndpointProvider = new AnthropicProvider(
                    httpClient, API_KEY, null, MODEL, MAX_TOKENS, TEMPERATURE, TIMEOUT_SECONDS, ANTHROPIC_VERSION);

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            nullEndpointProvider.analyze(buildMinimalContext(), "system prompt");

            verify(httpClient).send(argThat(request ->
                    request.uri().toString().equals("https://api.anthropic.com/v1/messages")
            ), any());
        }

        @Test
        @DisplayName("Uses custom endpoint when apiEndpoint is configured")
        void shouldUseCustomEndpoint() throws Exception {
            AnthropicProvider customProvider = new AnthropicProvider(
                    httpClient, API_KEY, "https://my-proxy.example.com", MODEL,
                    MAX_TOKENS, TEMPERATURE, TIMEOUT_SECONDS, ANTHROPIC_VERSION);

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            customProvider.analyze(buildMinimalContext(), "system prompt");

            verify(httpClient).send(argThat(request ->
                    request.uri().toString().equals("https://my-proxy.example.com/v1/messages")
            ), any());
        }

        @Test
        @DisplayName("Strips trailing slash from custom endpoint")
        void shouldStripTrailingSlash() throws Exception {
            AnthropicProvider trailingSlashProvider = new AnthropicProvider(
                    httpClient, API_KEY, "https://my-proxy.example.com/", MODEL,
                    MAX_TOKENS, TEMPERATURE, TIMEOUT_SECONDS, ANTHROPIC_VERSION);

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            trailingSlashProvider.analyze(buildMinimalContext(), "system prompt");

            verify(httpClient).send(argThat(request ->
                    request.uri().toString().equals("https://my-proxy.example.com/v1/messages")
            ), any());
        }
    }

    // ========== Task 2 Tests: Request Construction ==========

    @Nested
    @DisplayName("Request Construction (AC3)")
    class RequestConstruction {

        @Test
        @DisplayName("Sets x-api-key header (NOT Authorization: Bearer)")
        void shouldSetXApiKeyHeader() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            provider.analyze(buildMinimalContext(), "system prompt");

            verify(httpClient).send(argThat(request -> {
                String apiKeyHeader = request.headers().firstValue("x-api-key").orElse("");
                String authHeader = request.headers().firstValue("Authorization").orElse("");
                return apiKeyHeader.equals(API_KEY) && authHeader.isEmpty();
            }), any());
        }

        @Test
        @DisplayName("Sets anthropic-version header")
        void shouldSetAnthropicVersionHeader() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            provider.analyze(buildMinimalContext(), "system prompt");

            verify(httpClient).send(argThat(request ->
                    request.headers().firstValue("anthropic-version").orElse("")
                            .equals(ANTHROPIC_VERSION)
            ), any());
        }

        @Test
        @DisplayName("Sets Content-Type header to application/json")
        void shouldSetContentTypeHeader() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            provider.analyze(buildMinimalContext(), "system prompt");

            verify(httpClient).send(argThat(request ->
                    request.headers().firstValue("Content-Type").orElse("")
                            .equals("application/json")
            ), any());
        }

        @Test
        @DisplayName("Builds correct request body with system as top-level string")
        void shouldBuildCorrectRequestBody() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            provider.analyze(buildMinimalContext(), "You are a code review assistant");

            verify(httpClient).send(argThat(request -> {
                // Verify POST method
                assertThat(request.method()).isEqualTo("POST");

                // Extract request body
                String body = extractRequestBody(request);

                // Verify JSON structure
                assertThat(body).contains("\"model\":\"" + MODEL + "\"");
                assertThat(body).contains("\"max_tokens\":" + MAX_TOKENS);
                assertThat(body).contains("\"temperature\":" + TEMPERATURE);
                // system is a top-level string, NOT in messages array
                assertThat(body).contains("\"system\":\"You are a code review assistant\"");
                // messages array should only have user role
                assertThat(body).contains("\"role\":\"user\"");
                // Should NOT contain role:system in messages
                assertThat(body).doesNotContain("\"role\":\"system\"");
                return true;
            }), any());
        }
    }

    // ========== Task 3 Tests: Response Parsing ==========

    @Nested
    @DisplayName("Response Parsing (AC4)")
    class ResponseParsing {

        @Test
        @DisplayName("Parses successful response with issues and metadata")
        void shouldParseSuccessfulResponse() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(buildSuccessResponse());

            ReviewResult result = provider.analyze(buildMinimalContext(), "system prompt");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getIssues()).hasSize(1);
            ReviewIssue issue = result.getIssues().get(0);
            assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.HIGH);
            assertThat(issue.getCategory()).isEqualTo(IssueCategory.CORRECTNESS);
            assertThat(issue.getFilePath()).isEqualTo("src/Main.java");
            assertThat(issue.getLine()).isEqualTo(42);
            assertThat(issue.getMessage()).isEqualTo("Null pointer risk");
            assertThat(issue.getSuggestion()).isEqualTo("Add null check");

            assertThat(result.getMetadata()).isNotNull();
            assertThat(result.getMetadata().getProviderId()).isEqualTo("anthropic");
            assertThat(result.getMetadata().getModel()).isEqualTo(MODEL);
            assertThat(result.getMetadata().getPromptTokens()).isEqualTo(1500);
            assertThat(result.getMetadata().getCompletionTokens()).isEqualTo(800);
            assertThat(result.getMetadata().getDurationMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Parses response with empty issues list")
        void shouldParseEmptyIssues() throws Exception {
            String emptyIssuesResponse = """
                    {
                      "type": "message",
                      "content": [{"type": "text", "text": "{\\"issues\\": []}"}],
                      "usage": {"input_tokens": 100, "output_tokens": 50}
                    }""";

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(emptyIssuesResponse);

            ReviewResult result = provider.analyze(buildMinimalContext(), "system prompt");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getIssues()).isEmpty();
            assertThat(result.getMetadata().getPromptTokens()).isEqualTo(100);
            assertThat(result.getMetadata().getCompletionTokens()).isEqualTo(50);
        }

        @Test
        @DisplayName("Returns failed result for malformed JSON response")
        void shouldReturnFailedForMalformedJson() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("not valid json at all");

            ReviewResult result = provider.analyze(buildMinimalContext(), "system prompt");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Failed to parse");
        }

        @Test
        @DisplayName("Returns failed result when AI content is not valid JSON")
        void shouldReturnFailedForMalformedAiContent() throws Exception {
            String badContentResponse = """
                    {
                      "type": "message",
                      "content": [{"type": "text", "text": "This is not JSON"}],
                      "usage": {"input_tokens": 100, "output_tokens": 50}
                    }""";

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(badContentResponse);

            ReviewResult result = provider.analyze(buildMinimalContext(), "system prompt");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Failed to parse");
        }
    }

    // ========== Task 4 Tests: Error Handling and Retry ==========

    @Nested
    @DisplayName("Error Handling (AC5)")
    class ErrorHandling {

        @Test
        @DisplayName("429 throws RateLimitException")
        void shouldThrowRateLimitOn429() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(429);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\"}}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("401 throws AIAuthenticationException")
        void shouldThrowAuthExceptionOn401() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(401);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AIAuthenticationException.class);
        }

        @Test
        @DisplayName("500 throws AIProviderException")
        void shouldThrowProviderExceptionOn500() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(500);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"error\":{\"type\":\"api_error\"}}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AIProviderException.class);
        }

        @Test
        @DisplayName("529 throws AIProviderException (Anthropic overloaded)")
        void shouldThrowProviderExceptionOn529() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(529);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AIProviderException.class);
        }

        @Test
        @DisplayName("HttpTimeoutException wraps as AITimeoutException with no retry")
        void shouldThrowTimeoutExceptionOnHttpTimeout() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new HttpTimeoutException("Connection timed out"));

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AITimeoutException.class);

            // Verify no retry — only 1 call
            verify(httpClient, times(1)).send(any(), any());
        }

        @Test
        @DisplayName("408 throws AITimeoutException — not retried")
        void shouldThrowTimeoutExceptionOn408() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(408);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"error\":{\"type\":\"timeout_error\"}}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AITimeoutException.class);

            // 408 is NOT in retry range (not 429 and not >= 500), so only 1 call
            verify(httpClient, times(1)).send(any(), any());
        }

        @Test
        @DisplayName("504 throws AITimeoutException after retries exhausted")
        void shouldThrowTimeoutExceptionOn504() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(504);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"error\":{\"type\":\"timeout_error\"}}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AITimeoutException.class);

            // 504 >= 500, so it IS retried: initial + 2 retries = 3 calls
            verify(httpClient, times(3)).send(any(), any());
        }

        @Test
        @DisplayName("IOException wraps as AIProviderException with no retry")
        void shouldThrowProviderExceptionOnIOException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Network error"));

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AIProviderException.class);

            // Verify no retry — IOException throws immediately
            verify(httpClient, times(1)).send(any(), any());
        }

        @Test
        @DisplayName("InterruptedException restores interrupt flag and throws")
        void shouldRestoreInterruptFlagOnInterrupt() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new InterruptedException("Interrupted"));

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AIProviderException.class);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // Clear interrupt flag for test cleanup
            Thread.interrupted();
        }
    }

    // ========== Task 4 Tests: Retry Logic ==========

    @Nested
    @DisplayName("Retry Logic (AC6)")
    class RetryLogic {

        @Test
        @DisplayName("Retries on 429 then succeeds on 2nd attempt")
        void shouldRetryOn429ThenSucceed() throws Exception {
            // First call returns 429 (retry path does NOT call body()), second call returns 200
            HttpResponse<String> errorResponse = mock(HttpResponse.class);
            when(errorResponse.statusCode()).thenReturn(429);

            HttpResponse<String> successResponse = mock(HttpResponse.class);
            when(successResponse.statusCode()).thenReturn(200);
            when(successResponse.body()).thenReturn(buildSuccessResponse());

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(errorResponse, successResponse);

            ReviewResult result = provider.analyze(buildMinimalContext(), "prompt");

            assertThat(result.isSuccess()).isTrue();
            verify(httpClient, times(2)).send(any(), any());
        }

        @Test
        @DisplayName("Retries on 500 then succeeds on 2nd attempt")
        void shouldRetryOn500ThenSucceed() throws Exception {
            HttpResponse<String> errorResponse = mock(HttpResponse.class);
            when(errorResponse.statusCode()).thenReturn(500);

            HttpResponse<String> successResponse = mock(HttpResponse.class);
            when(successResponse.statusCode()).thenReturn(200);
            when(successResponse.body()).thenReturn(buildSuccessResponse());

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(errorResponse, successResponse);

            ReviewResult result = provider.analyze(buildMinimalContext(), "prompt");

            assertThat(result.isSuccess()).isTrue();
            verify(httpClient, times(2)).send(any(), any());
        }

        @Test
        @DisplayName("Retries on 529 then succeeds on 2nd attempt")
        void shouldRetryOn529ThenSucceed() throws Exception {
            HttpResponse<String> errorResponse = mock(HttpResponse.class);
            when(errorResponse.statusCode()).thenReturn(529);

            HttpResponse<String> successResponse = mock(HttpResponse.class);
            when(successResponse.statusCode()).thenReturn(200);
            when(successResponse.body()).thenReturn(buildSuccessResponse());

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(errorResponse, successResponse);

            ReviewResult result = provider.analyze(buildMinimalContext(), "prompt");

            assertThat(result.isSuccess()).isTrue();
            verify(httpClient, times(2)).send(any(), any());
        }

        @Test
        @DisplayName("Max retries exhausted throws RateLimitException for 429")
        void shouldThrowAfterMaxRetriesExhausted() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(429);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\"}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(RateLimitException.class);

            // Initial attempt + 2 retries = 3 total calls
            verify(httpClient, times(3)).send(any(), any());
        }

        @Test
        @DisplayName("No retry on 401 — throws immediately")
        void shouldNotRetryOn401() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(401);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\"}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AIAuthenticationException.class);

            verify(httpClient, times(1)).send(any(), any());
        }

        @Test
        @DisplayName("No retry on 404 — throws immediately")
        void shouldNotRetryOn404() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(404);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\"}}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AIProviderException.class);

            verify(httpClient, times(1)).send(any(), any());
        }

        @Test
        @DisplayName("No retry on 403 — throws immediately")
        void shouldNotRetryOn403() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(403);
            when(httpResponse.body()).thenReturn("{\"type\":\"error\"}");

            assertThatThrownBy(() -> provider.analyze(buildMinimalContext(), "prompt"))
                    .isInstanceOf(AIProviderException.class);

            verify(httpClient, times(1)).send(any(), any());
        }
    }

    // ========== Helper Methods ==========

    private CodeContext buildMinimalContext() {
        return CodeContext.builder()
                .rawDiff("--- a/file.java\n+++ b/file.java\n@@ -1,3 +1,3 @@\n-old\n+new")
                .files(List.of(FileInfo.builder()
                        .path("src/Main.java")
                        .changeType(ChangeType.MODIFY)
                        .language(Language.JAVA)
                        .build()))
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(1)
                        .totalLinesAdded(1)
                        .totalLinesDeleted(1)
                        .build())
                .taskMeta(TaskMetadata.builder()
                        .prTitle("Test PR")
                        .author("testuser")
                        .branch("feature/test")
                        .build())
                .fileContents(Map.of("src/Main.java", "public class Main {}"))
                .build();
    }

    private String buildSuccessResponse() {
        return """
                {
                  "id": "msg_test123",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\\"issues\\": [{\\"severity\\": \\"HIGH\\", \\"category\\": \\"CORRECTNESS\\", \\"filePath\\": \\"src/Main.java\\", \\"line\\": 42, \\"message\\": \\"Null pointer risk\\", \\"suggestion\\": \\"Add null check\\"}]}"
                    }
                  ],
                  "model": "claude-sonnet-4-5-20250929",
                  "stop_reason": "end_turn",
                  "usage": {
                    "input_tokens": 1500,
                    "output_tokens": 800
                  }
                }""";
    }

    /**
     * Extracts request body string from HttpRequest using Flow.Subscriber.
     */
    private String extractRequestBody(HttpRequest request) {
        var bodyPublisher = request.bodyPublisher().orElseThrow();
        var subscriber = new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            private final StringBuilder sb = new StringBuilder();

            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(java.nio.ByteBuffer item) {
                sb.append(new String(item.array(), java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }

            public String getBody() {
                return sb.toString();
            }
        };
        bodyPublisher.subscribe(subscriber);
        return subscriber.getBody();
    }
}
