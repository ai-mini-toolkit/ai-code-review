package com.aicodereview.integration.im;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DingTalkWebhookServiceImpl.
 *
 * @since 7.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DingTalkWebhookServiceImpl Unit Tests")
class DingTalkWebhookServiceImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private DingTalkWebhookServiceImpl service;

    private static final String WEBHOOK_URL = "https://oapi.dingtalk.com/robot/send?access_token=test123";
    private static final String SECRET = "SEC_test_secret_key";
    private static final String TITLE = "AI Code Review Alert";
    private static final String TEXT = "### Warning\n\nThreshold violated.";

    @BeforeEach
    void setUp() {
        service = new DingTalkWebhookServiceImpl(httpClient);
    }

    @Nested
    @DisplayName("sendNotification")
    class SendNotification {

        @Test
        @DisplayName("Should send successfully when HTTP 200 and errcode=0")
        void shouldSendSuccessfully() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("{\"errcode\": 0, \"errmsg\": \"ok\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, null, TITLE, TEXT);

            assertThat(result).isTrue();

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            HttpRequest request = captor.getValue();
            assertThat(request.uri().toString()).isEqualTo(WEBHOOK_URL);
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.headers().firstValue("Content-Type"))
                    .hasValue("application/json; charset=utf-8");
        }

        @Test
        @DisplayName("Should append signature parameters when secret is provided")
        void shouldAppendSignatureWhenSecretProvided() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("{\"errcode\": 0, \"errmsg\": \"ok\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, SECRET, TITLE, TEXT);

            assertThat(result).isTrue();

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            String uri = captor.getValue().uri().toString();
            assertThat(uri).contains("timestamp=");
            assertThat(uri).contains("sign=");
            assertThat(uri).startsWith(WEBHOOK_URL + "&");
        }

        @Test
        @DisplayName("Should return false on HTTP error response")
        void shouldReturnFalseOnHttpError() throws Exception {
            when(httpResponse.statusCode()).thenReturn(500);
            when(httpResponse.body()).thenReturn("Internal Server Error");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, null, TITLE, TEXT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false on non-zero errcode")
        void shouldReturnFalseOnNonZeroErrcode() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("{\"errcode\": 310000, \"errmsg\": \"keywords not in content\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, null, TITLE, TEXT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false on IOException")
        void shouldReturnFalseOnIOException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new IOException("Connection refused"));

            boolean result = service.sendNotification(WEBHOOK_URL, null, TITLE, TEXT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when webhook URL is blank")
        void shouldReturnFalseWhenUrlBlank() {
            boolean result = service.sendNotification("", null, TITLE, TEXT);

            assertThat(result).isFalse();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should return false on InterruptedException and restore interrupt flag")
        void shouldReturnFalseOnInterruptedException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new InterruptedException("Interrupted"));

            boolean result = service.sendNotification(WEBHOOK_URL, null, TITLE, TEXT);

            assertThat(result).isFalse();
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Nested
    @DisplayName("appendSignature")
    class AppendSignature {

        @Test
        @DisplayName("Should return original URL when secret is null")
        void shouldReturnOriginalUrlWhenSecretNull() throws Exception {
            String result = service.appendSignature(WEBHOOK_URL, null);
            assertThat(result).isEqualTo(WEBHOOK_URL);
        }

        @Test
        @DisplayName("Should return original URL when secret is blank")
        void shouldReturnOriginalUrlWhenSecretBlank() throws Exception {
            String result = service.appendSignature(WEBHOOK_URL, "");
            assertThat(result).isEqualTo(WEBHOOK_URL);
        }

        @Test
        @DisplayName("Should append timestamp and sign when secret is provided")
        void shouldAppendTimestampAndSign() throws Exception {
            String result = service.appendSignature(WEBHOOK_URL, SECRET);
            assertThat(result).startsWith(WEBHOOK_URL + "&timestamp=");
            assertThat(result).contains("&sign=");
        }
    }

    @Nested
    @DisplayName("buildRequestBody")
    class BuildRequestBody {

        @Test
        @DisplayName("Should build valid DingTalk Markdown message body")
        void shouldBuildValidMarkdownBody() throws Exception {
            String body = service.buildRequestBody("Test Title", "### Hello");
            assertThat(body).contains("\"msgtype\":\"markdown\"");
            assertThat(body).contains("\"title\":\"Test Title\"");
            assertThat(body).contains("\"text\":\"### Hello\"");
        }
    }
}
