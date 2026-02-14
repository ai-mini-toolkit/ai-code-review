package com.aicodereview.integration.git;

import com.aicodereview.common.enums.GitPlatform;
import com.aicodereview.common.exception.GitApiException;
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
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GitHubApiClient Tests")
@ExtendWith(MockitoExtension.class)
class GitHubApiClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GitHubApiClient client;

    @BeforeEach
    void setUp() {
        client = new GitHubApiClient(httpClient, "test-token");
    }

    @Nested
    @DisplayName("getPlatform")
    class GetPlatform {

        @Test
        @DisplayName("Should return GITHUB")
        void shouldReturnGitHub() {
            assertThat(client.getPlatform()).isEqualTo(GitPlatform.GITHUB);
        }
    }

    @Nested
    @DisplayName("parseOwnerRepo")
    class ParseOwnerRepo {

        @Test
        @DisplayName("Should extract owner/repo from standard URL")
        void shouldExtractOwnerRepo() {
            assertThat(client.parseOwnerRepo("https://github.com/owner/repo"))
                    .isEqualTo("owner/repo");
        }

        @Test
        @DisplayName("Should strip .git suffix")
        void shouldStripGitSuffix() {
            assertThat(client.parseOwnerRepo("https://github.com/owner/repo.git"))
                    .isEqualTo("owner/repo");
        }

        @Test
        @DisplayName("Should throw for null URL")
        void shouldThrowForNull() {
            assertThatThrownBy(() -> client.parseOwnerRepo(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should throw for empty URL")
        void shouldThrowForEmpty() {
            assertThatThrownBy(() -> client.parseOwnerRepo(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getFileContent")
    class GetFileContent {

        @Test
        @DisplayName("Should call correct URL and decode Base64 content")
        void shouldDecodeBase64Content() throws Exception {
            String originalContent = "public class App {}";
            String base64 = Base64.getEncoder().encodeToString(
                    originalContent.getBytes(StandardCharsets.UTF_8));
            String jsonResponse = String.format(
                    "{\"content\":\"%s\",\"encoding\":\"base64\"}", base64);

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(jsonResponse);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getFileContent(
                    "https://github.com/owner/repo", "abc123", "src/App.java");

            assertThat(result).isEqualTo(originalContent);

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            HttpRequest request = captor.getValue();
            assertThat(request.uri().toString()).contains(
                    "/repos/owner/repo/contents/src/App.java?ref=abc123");
            assertThat(request.headers().firstValue("Authorization"))
                    .hasValue("Bearer test-token");
        }

        @Test
        @DisplayName("Should URL-encode special characters in file path")
        void shouldEncodeSpecialCharsInFilePath() throws Exception {
            String base64 = Base64.getEncoder().encodeToString(
                    "content".getBytes(StandardCharsets.UTF_8));
            String jsonResponse = String.format(
                    "{\"content\":\"%s\",\"encoding\":\"base64\"}", base64);

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(jsonResponse);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            client.getFileContent(
                    "https://github.com/owner/repo", "sha", "src/My File.java");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            String url = captor.getValue().uri().toString();
            // Space should be encoded, path separator '/' preserved
            assertThat(url).contains("/contents/src/My+File.java");
        }
    }

    @Nested
    @DisplayName("getDiff (commit)")
    class GetCommitDiff {

        @Test
        @DisplayName("Should call commit URL with diff Accept header")
        void shouldCallCommitUrl() throws Exception {
            String diffContent = "diff --git a/f.java b/f.java\n--- a/f.java\n+++ b/f.java\n";
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(diffContent);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://github.com/owner/repo", "sha123");
            assertThat(result).isEqualTo(diffContent);

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            HttpRequest request = captor.getValue();
            assertThat(request.uri().toString()).contains("/repos/owner/repo/commits/sha123");
            assertThat(request.headers().firstValue("Accept"))
                    .hasValue("application/vnd.github.diff");
        }
    }

    @Nested
    @DisplayName("getDiff (branch comparison)")
    class GetBranchDiff {

        @Test
        @DisplayName("Should call compare URL with base...head")
        void shouldCallCompareUrl() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("diff content");
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            client.getDiff("https://github.com/owner/repo", "main", "feature");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            assertThat(captor.getValue().uri().toString())
                    .contains("/repos/owner/repo/compare/main...feature");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should throw GitApiException for 404")
        void shouldThrowFor404() throws Exception {
            when(httpResponse.statusCode()).thenReturn(404);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            assertThatThrownBy(() -> client.getDiff(
                    "https://github.com/owner/repo", "bad-sha"))
                    .isInstanceOf(GitApiException.class)
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("Should throw GitApiException for 403")
        void shouldThrowFor403() throws Exception {
            when(httpResponse.statusCode()).thenReturn(403);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            assertThatThrownBy(() -> client.getDiff(
                    "https://github.com/owner/repo", "sha"))
                    .isInstanceOf(GitApiException.class)
                    .hasMessageContaining("403");
        }

        @Test
        @DisplayName("Should wrap HttpTimeoutException as GitApiException")
        void shouldWrapTimeout() throws Exception {
            doThrow(new HttpTimeoutException("timed out"))
                    .when(httpClient).send(any(HttpRequest.class), any());

            assertThatThrownBy(() -> client.getDiff(
                    "https://github.com/owner/repo", "sha"))
                    .isInstanceOf(GitApiException.class)
                    .hasMessageContaining("timed out");
        }

        @Test
        @DisplayName("Should wrap IOException as GitApiException")
        void shouldWrapIOException() throws Exception {
            doThrow(new IOException("connection reset"))
                    .when(httpClient).send(any(HttpRequest.class), any());

            assertThatThrownBy(() -> client.getDiff(
                    "https://github.com/owner/repo", "sha"))
                    .isInstanceOf(GitApiException.class)
                    .hasMessageContaining("failed");
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryLogic {

        @Test
        @DisplayName("Should retry on 500 and succeed")
        void shouldRetryOn500() throws Exception {
            HttpResponse<String> failResponse = mock(HttpResponse.class);
            when(failResponse.statusCode()).thenReturn(500);

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("ok");

            doReturn(failResponse).doReturn(httpResponse)
                    .when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://github.com/owner/repo", "sha");
            assertThat(result).isEqualTo("ok");
            verify(httpClient, times(2)).send(any(), any());
        }

        @Test
        @DisplayName("Should retry on 429 and succeed")
        void shouldRetryOn429() throws Exception {
            HttpResponse<String> failResponse = mock(HttpResponse.class);
            when(failResponse.statusCode()).thenReturn(429);

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("ok");

            doReturn(failResponse).doReturn(httpResponse)
                    .when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://github.com/owner/repo", "sha");
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("Should NOT retry on 404")
        void shouldNotRetryOn404() throws Exception {
            when(httpResponse.statusCode()).thenReturn(404);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            assertThatThrownBy(() -> client.getDiff(
                    "https://github.com/owner/repo", "sha"))
                    .isInstanceOf(GitApiException.class);
            verify(httpClient, times(1)).send(any(), any());
        }

        @Test
        @DisplayName("Should fail after max retries exhausted")
        void shouldFailAfterMaxRetries() throws Exception {
            when(httpResponse.statusCode()).thenReturn(500);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            assertThatThrownBy(() -> client.getDiff(
                    "https://github.com/owner/repo", "sha"))
                    .isInstanceOf(GitApiException.class)
                    .hasMessageContaining("500");
            verify(httpClient, times(3)).send(any(), any()); // 1 + 2 retries
        }
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("Should not set Authorization when token is empty")
        void shouldNotSetAuthWhenEmpty() throws Exception {
            GitHubApiClient noTokenClient = new GitHubApiClient(httpClient, "");
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("diff");
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            noTokenClient.getDiff("https://github.com/owner/repo", "sha");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            assertThat(captor.getValue().headers().firstValue("Authorization")).isEmpty();
        }
    }
}
