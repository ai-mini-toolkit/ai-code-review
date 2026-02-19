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
 * Unit tests for SlackWebhookServiceImpl.
 *
 * @since 7.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlackWebhookServiceImpl Unit Tests")
class SlackWebhookServiceImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private SlackWebhookServiceImpl service;

    private static final String WEBHOOK_URL = "https://hooks.slack.com/services/T00/B00/xxx";
    private static final String TEXT = ":warning: *AI Code Review Threshold Violation*\n\n*Project:* Test";

    @BeforeEach
    void setUp() {
        service = new SlackWebhookServiceImpl(httpClient);
    }

    @Nested
    @DisplayName("sendNotification")
    class SendNotification {

        @Test
        @DisplayName("Should send successfully when HTTP 200")
        void shouldSendSuccessfully() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, TEXT);

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
            when(httpResponse.statusCode()).thenReturn(404);
            when(httpResponse.body()).thenReturn("channel_not_found");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            boolean result = service.sendNotification(WEBHOOK_URL, TEXT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false on IOException")
        void shouldReturnFalseOnIOException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new IOException("Connection refused"));

            boolean result = service.sendNotification(WEBHOOK_URL, TEXT);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when webhook URL is blank")
        void shouldReturnFalseWhenUrlBlank() {
            boolean result = service.sendNotification("", TEXT);

            assertThat(result).isFalse();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should return false on InterruptedException and restore interrupt flag")
        void shouldReturnFalseOnInterruptedException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new InterruptedException("Interrupted"));

            boolean result = service.sendNotification(WEBHOOK_URL, TEXT);

            assertThat(result).isFalse();
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Nested
    @DisplayName("buildRequestBody")
    class BuildRequestBody {

        @Test
        @DisplayName("Should build valid Slack message body")
        void shouldBuildValidBody() throws Exception {
            String body = service.buildRequestBody("Hello *bold*");
            assertThat(body).contains("\"text\":\"Hello *bold*\"");
        }
    }
}
