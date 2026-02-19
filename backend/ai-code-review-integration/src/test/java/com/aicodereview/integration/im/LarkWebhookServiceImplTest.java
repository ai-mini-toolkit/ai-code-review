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
 * Unit tests for LarkWebhookServiceImpl.
 *
 * @since 7.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LarkWebhookServiceImpl Unit Tests")
class LarkWebhookServiceImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private LarkWebhookServiceImpl service;

    private static final String WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/test123";
    private static final String TITLE = "AI Code Review Alert";
    private static final String CONTENT = "**Project:** Test\n**Branch:** main";

    @BeforeEach
    void setUp() {
        service = new LarkWebhookServiceImpl(httpClient);
    }

    @Nested
    @DisplayName("sendNotification")
    class SendNotification {

        @Test
        @DisplayName("Should send successfully when HTTP 200 and code=0")
        void shouldSendSuccessfully() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("{\"code\": 0, \"msg\": \"success\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, TITLE, CONTENT);

            assertThat(result).isTrue();

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            HttpRequest request = captor.getValue();
            assertThat(request.uri().toString()).isEqualTo(WEBHOOK_URL);
            assertThat(request.method()).isEqualTo("POST");
        }

        @Test
        @DisplayName("Should return false on HTTP error response")
        void shouldReturnFalseOnHttpError() throws Exception {
            when(httpResponse.statusCode()).thenReturn(400);
            when(httpResponse.body()).thenReturn("Bad Request");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, TITLE, CONTENT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false on non-zero code")
        void shouldReturnFalseOnNonZeroCode() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("{\"code\": 19001, \"msg\": \"param invalid\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, TITLE, CONTENT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false on IOException")
        void shouldReturnFalseOnIOException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new IOException("Connection refused"));

            boolean result = service.sendNotification(WEBHOOK_URL, TITLE, CONTENT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when webhook URL is blank")
        void shouldReturnFalseWhenUrlBlank() {
            boolean result = service.sendNotification("", TITLE, CONTENT);

            assertThat(result).isFalse();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should return false on InterruptedException and restore interrupt flag")
        void shouldReturnFalseOnInterruptedException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new InterruptedException("Interrupted"));

            boolean result = service.sendNotification(WEBHOOK_URL, TITLE, CONTENT);

            assertThat(result).isFalse();
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Nested
    @DisplayName("buildRequestBody")
    class BuildRequestBody {

        @Test
        @DisplayName("Should build valid Lark interactive card body")
        void shouldBuildValidCardBody() throws Exception {
            String body = service.buildRequestBody("Test Title", "**Hello**");
            assertThat(body).contains("\"msg_type\":\"interactive\"");
            assertThat(body).contains("\"tag\":\"plain_text\"");
            assertThat(body).contains("\"content\":\"Test Title\"");
            assertThat(body).contains("\"tag\":\"markdown\"");
            assertThat(body).contains("\"content\":\"**Hello**\"");
        }
    }
}
