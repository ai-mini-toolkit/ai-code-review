package com.aicodereview.integration.ai.openai;

import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.FileInfo;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.IssueSeverity;
import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.Language;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.common.exception.AIAuthenticationException;
import com.aicodereview.common.exception.AIProviderException;
import com.aicodereview.common.exception.AITimeoutException;
import com.aicodereview.common.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OpenAICompatibleProvider}.
 *
 * @since 4.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenAICompatibleProvider Tests")
class OpenAICompatibleProviderTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private OpenAICompatibleProvider provider;

    private CodeContext sampleContext;

    private static final String SAMPLE_SUCCESS_RESPONSE = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\\"issues\\": [{\\"severity\\": \\"HIGH\\", \\"category\\": \\"SECURITY\\", \\"filePath\\": \\"src/Main.java\\", \\"line\\": 42, \\"message\\": \\"SQL injection vulnerability\\", \\"suggestion\\": \\"Use parameterized queries\\"}]}"
                  }
                }
              ],
              "usage": {
                "prompt_tokens": 1500,
                "completion_tokens": 800
              }
            }
            """;

    private static final String SAMPLE_EMPTY_ISSUES_RESPONSE = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\\"issues\\": []}"
                  }
                }
              ],
              "usage": {
                "prompt_tokens": 1000,
                "completion_tokens": 200
              }
            }
            """;

    @BeforeEach
    void setUp() {
        provider = new OpenAICompatibleProvider(
                httpClient,
                "test-api-key",
                "https://api.openai.com",
                "gpt-4",
                4000,
                0.3,
                30
        );

        sampleContext = CodeContext.builder()
                .rawDiff("--- a/src/Main.java\n+++ b/src/Main.java\n@@ -1,3 +1,5 @@\n+import foo;\n public class Main {}")
                .files(List.of(FileInfo.builder()
                        .path("src/Main.java")
                        .changeType(ChangeType.MODIFY)
                        .language(Language.JAVA)
                        .build()))
                .fileContents(Map.of("src/Main.java", "public class Main {}"))
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(1)
                        .totalLinesAdded(2)
                        .totalLinesDeleted(0)
                        .build())
                .taskMeta(TaskMetadata.builder()
                        .prTitle("Fix SQL injection")
                        .author("dev")
                        .branch("feature/fix")
                        .commitHash("abc123")
                        .taskType(TaskType.PULL_REQUEST)
                        .build())
                .build();
    }

    // --- AC1/AC8: Provider identity and availability ---

    @Test
    @DisplayName("getProviderId() should return 'openai'")
    void getProviderIdShouldReturnOpenai() {
        assertThat(provider.getProviderId()).isEqualTo("openai");
    }

    @Test
    @DisplayName("isAvailable() should return true when apiKey is configured")
    void isAvailableShouldReturnTrueWhenApiKeyConfigured() {
        assertThat(provider.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("isAvailable() should return false when apiKey is empty")
    void isAvailableShouldReturnFalseWhenApiKeyEmpty() {
        OpenAICompatibleProvider emptyKeyProvider = new OpenAICompatibleProvider(
                httpClient, "", "https://api.openai.com", "gpt-4", 4000, 0.3, 30);
        assertThat(emptyKeyProvider.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("isAvailable() should return false when apiKey is null")
    void isAvailableShouldReturnFalseWhenApiKeyNull() {
        OpenAICompatibleProvider nullKeyProvider = new OpenAICompatibleProvider(
                httpClient, null, "https://api.openai.com", "gpt-4", 4000, 0.3, 30);
        assertThat(nullKeyProvider.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("getMaxTokens() should return configured value")
    void getMaxTokensShouldReturnConfiguredValue() {
        assertThat(provider.getMaxTokens()).isEqualTo(4000);
    }

    // --- AC2: Configurable baseUrl ---

    @Test
    @DisplayName("should use official OpenAI endpoint when apiEndpoint is default")
    void shouldUseOfficialEndpoint() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        provider.analyze(sampleContext, "Review this code");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    @DisplayName("should use custom endpoint when configured")
    void shouldUseCustomEndpoint() throws Exception {
        OpenAICompatibleProvider customProvider = new OpenAICompatibleProvider(
                httpClient, "test-key", "https://my-proxy.example.com", "gpt-4", 4000, 0.3, 30);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        customProvider.analyze(sampleContext, "Review this code");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .isEqualTo("https://my-proxy.example.com/v1/chat/completions");
    }

    @Test
    @DisplayName("should default to official endpoint when apiEndpoint is null")
    void shouldDefaultToOfficialEndpointWhenNull() throws Exception {
        OpenAICompatibleProvider defaultProvider = new OpenAICompatibleProvider(
                httpClient, "test-key", null, "gpt-4", 4000, 0.3, 30);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        defaultProvider.analyze(sampleContext, "Review this code");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    @DisplayName("should default to official endpoint when apiEndpoint is empty")
    void shouldDefaultToOfficialEndpointWhenEmpty() throws Exception {
        OpenAICompatibleProvider defaultProvider = new OpenAICompatibleProvider(
                httpClient, "test-key", "", "gpt-4", 4000, 0.3, 30);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        defaultProvider.analyze(sampleContext, "Review this code");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    // --- AC3: Request construction ---

    @Test
    @DisplayName("should set Authorization Bearer header")
    void shouldSetAuthorizationHeader() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        provider.analyze(sampleContext, "Review this code");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertThat(request.headers().firstValue("Authorization"))
                .hasValue("Bearer test-api-key");
        assertThat(request.headers().firstValue("Content-Type"))
                .hasValue("application/json");
    }

    @Test
    @DisplayName("should build correct request body JSON structure")
    void shouldBuildCorrectRequestBody() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        provider.analyze(sampleContext, "You are a code review assistant");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();

        // Verify it's a POST request
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.bodyPublisher()).isPresent();

        // Extract and verify actual request body JSON
        String body = extractRequestBody(request);
        ObjectMapper om = new ObjectMapper();
        JsonNode json = om.readTree(body);

        assertThat(json.get("model").asText()).isEqualTo("gpt-4");
        assertThat(json.get("max_tokens").asInt()).isEqualTo(4000);
        assertThat(json.get("temperature").asDouble()).isEqualTo(0.3);
        assertThat(json.path("response_format").path("type").asText()).isEqualTo("json_object");

        JsonNode messages = json.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("You are a code review assistant");
        assertThat(messages.get(1).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).get("content").asText()).contains("src/Main.java");
    }

    /**
     * Extracts the request body string from an HttpRequest by subscribing to its BodyPublisher.
     */
    private String extractRequestBody(HttpRequest request) {
        List<ByteBuffer> buffers = new ArrayList<>();
        request.bodyPublisher().ifPresent(publisher ->
                publisher.subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ByteBuffer item) {
                        buffers.add(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                    }

                    @Override
                    public void onComplete() {
                    }
                }));
        int total = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
        byte[] bytes = new byte[total];
        int pos = 0;
        for (ByteBuffer buf : buffers) {
            int len = buf.remaining();
            buf.get(bytes, pos, len);
            pos += len;
        }
        return new String(bytes);
    }

    // --- AC4: Response parsing ---

    @Test
    @DisplayName("should parse successful response with issues and metadata")
    void shouldParseSuccessfulResponse() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        ReviewResult result = provider.analyze(sampleContext, "Review this code");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIssues()).hasSize(1);
        assertThat(result.getIssues().get(0).getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(result.getIssues().get(0).getCategory()).isEqualTo(IssueCategory.SECURITY);
        assertThat(result.getIssues().get(0).getFilePath()).isEqualTo("src/Main.java");
        assertThat(result.getIssues().get(0).getLine()).isEqualTo(42);
        assertThat(result.getIssues().get(0).getMessage()).isEqualTo("SQL injection vulnerability");
        assertThat(result.getIssues().get(0).getSuggestion()).isEqualTo("Use parameterized queries");
        assertThat(result.getMetadata().getProviderId()).isEqualTo("openai");
        assertThat(result.getMetadata().getModel()).isEqualTo("gpt-4");
        assertThat(result.getMetadata().getPromptTokens()).isEqualTo(1500);
        assertThat(result.getMetadata().getCompletionTokens()).isEqualTo(800);
        assertThat(result.getMetadata().getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("should parse response with empty issues list")
    void shouldParseEmptyIssuesResponse() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_EMPTY_ISSUES_RESPONSE);

        ReviewResult result = provider.analyze(sampleContext, "Review this code");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIssues()).isEmpty();
        assertThat(result.getMetadata().getPromptTokens()).isEqualTo(1000);
        assertThat(result.getMetadata().getCompletionTokens()).isEqualTo(200);
    }

    @Test
    @DisplayName("should return failed result for malformed JSON response")
    void shouldReturnFailedForMalformedResponse() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("not valid json at all");

        ReviewResult result = provider.analyze(sampleContext, "Review this code");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Failed to parse");
    }

    @Test
    @DisplayName("should return failed result when AI content is malformed JSON")
    void shouldReturnFailedWhenAIContentMalformed() throws Exception {
        String malformedContent = """
                {
                  "choices": [{"message": {"content": "This is not JSON at all"}}],
                  "usage": {"prompt_tokens": 100, "completion_tokens": 50}
                }
                """;
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(malformedContent);

        ReviewResult result = provider.analyze(sampleContext, "Review this code");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Failed to parse");
    }

    // --- AC5: Error handling ---

    @Test
    @DisplayName("should throw RateLimitException on 429")
    void shouldThrowRateLimitExceptionOn429() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpResponse.body()).thenReturn("{\"error\": \"rate limit exceeded\"}");

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("should throw AIAuthenticationException on 401")
    void shouldThrowAuthExceptionOn401() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpResponse.body()).thenReturn("{\"error\": \"invalid api key\"}");

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(AIAuthenticationException.class);
    }

    @Test
    @DisplayName("should throw AIProviderException on 500")
    void shouldThrowProviderExceptionOn500() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("{\"error\": \"internal server error\"}");

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(AIProviderException.class);
    }

    @Test
    @DisplayName("should throw AITimeoutException on HttpTimeoutException (no retry)")
    void shouldThrowTimeoutExceptionOnHttpTimeout() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("connect timed out"));

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(AITimeoutException.class);

        // Should NOT retry on timeout — only 1 call
        verify(httpClient, times(1)).send(any(), any());
    }

    // --- AC6: Retry logic ---

    @Test
    @DisplayName("should retry on 429 then succeed on 2nd attempt")
    void shouldRetryOn429ThenSucceed() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode())
                .thenReturn(429)
                .thenReturn(200);
        // body() is only called once (on 200 success) — retry path doesn't read body
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        ReviewResult result = provider.analyze(sampleContext, "Review");

        assertThat(result.isSuccess()).isTrue();
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("should retry on 500 then succeed on 2nd attempt")
    void shouldRetryOn500ThenSucceed() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode())
                .thenReturn(500)
                .thenReturn(200);
        // body() is only called once (on 200 success) — retry path doesn't read body
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        ReviewResult result = provider.analyze(sampleContext, "Review");

        assertThat(result.isSuccess()).isTrue();
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("should throw after max retries exhausted (3x 429)")
    void shouldThrowAfterMaxRetriesExhausted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpResponse.body()).thenReturn("{\"error\": \"rate limit\"}");

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(RateLimitException.class);

        // Initial attempt + 2 retries = 3 total calls
        verify(httpClient, times(3)).send(any(), any());
    }

    @Test
    @DisplayName("should NOT retry on 401")
    void shouldNotRetryOn401() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpResponse.body()).thenReturn("{\"error\": \"unauthorized\"}");

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(AIAuthenticationException.class);

        // Only 1 call — no retry
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("should NOT retry on 403")
    void shouldNotRetryOn403() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.body()).thenReturn("{\"error\": \"forbidden\"}");

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(AIProviderException.class);

        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("should wrap IOException as AIProviderException")
    void shouldWrapIOExceptionAsProviderException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(AIProviderException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("should handle InterruptedException by restoring interrupt flag")
    void shouldHandleInterruptedException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));

        assertThatThrownBy(() -> provider.analyze(sampleContext, "Review"))
                .isInstanceOf(AIProviderException.class);

        // Verify interrupt flag was restored
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // Clear the flag for test cleanup
        Thread.interrupted();
    }

    // --- AC2: Trailing slash handling ---

    @Test
    @DisplayName("should strip trailing slash from apiEndpoint")
    void shouldStripTrailingSlash() throws Exception {
        OpenAICompatibleProvider slashProvider = new OpenAICompatibleProvider(
                httpClient, "test-key", "https://api.openai.com/", "gpt-4", 4000, 0.3, 30);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_SUCCESS_RESPONSE);

        slashProvider.analyze(sampleContext, "Review");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .isEqualTo("https://api.openai.com/v1/chat/completions");
    }
}
