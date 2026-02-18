package com.aicodereview.integration.git;

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
 * Unit tests for GitHubPRCommentServiceImpl.
 *
 * @since 7.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubPRCommentServiceImpl Unit Tests")
class GitHubPRCommentServiceImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GitHubPRCommentServiceImpl service;

    private static final String REPO_URL = "https://github.com/owner/repo";
    private static final Integer PR_NUMBER = 42;
    private static final String COMMENT_BODY = "## AI Code Review\n\nAll checks passed.";

    @BeforeEach
    void setUp() {
        service = new GitHubPRCommentServiceImpl(httpClient, "test-token");
    }

    @Nested
    @DisplayName("postComment")
    class PostComment {

        @Test
        @DisplayName("Should post comment successfully and return comment ID")
        void shouldPostCommentSuccessfully() throws Exception {
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 12345, \"html_url\": \"https://github.com/owner/repo/issues/42#issuecomment-12345\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            Long commentId = service.postComment(REPO_URL, PR_NUMBER, COMMENT_BODY);

            assertThat(commentId).isEqualTo(12345L);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());
            HttpRequest request = requestCaptor.getValue();
            assertThat(request.uri().toString()).isEqualTo("https://api.github.com/repos/owner/repo/issues/42/comments");
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.headers().firstValue("Authorization")).hasValue("Bearer test-token");
            assertThat(request.headers().firstValue("Accept")).hasValue("application/vnd.github+json");
        }

        @Test
        @DisplayName("Should skip when token not configured")
        void shouldSkipWhenNoToken() {
            GitHubPRCommentServiceImpl noTokenService =
                    new GitHubPRCommentServiceImpl(httpClient, "");

            Long result = noTokenService.postComment(REPO_URL, PR_NUMBER, COMMENT_BODY);

            assertThat(result).isNull();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should skip when token is null")
        void shouldSkipWhenTokenNull() {
            GitHubPRCommentServiceImpl nullTokenService =
                    new GitHubPRCommentServiceImpl(httpClient, null);

            Long result = nullTokenService.postComment(REPO_URL, PR_NUMBER, COMMENT_BODY);

            assertThat(result).isNull();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should skip when PR number is null")
        void shouldSkipWhenPrNumberNull() {
            Long result = service.postComment(REPO_URL, null, COMMENT_BODY);

            assertThat(result).isNull();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should return null on HTTP error response")
        void shouldReturnNullOnHttpError() throws Exception {
            when(httpResponse.statusCode()).thenReturn(403);
            when(httpResponse.body()).thenReturn("{\"message\": \"Resource not accessible by integration\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            Long result = service.postComment(REPO_URL, PR_NUMBER, COMMENT_BODY);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null on IOException")
        void shouldReturnNullOnIOException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new IOException("Connection refused"));

            Long result = service.postComment(REPO_URL, PR_NUMBER, COMMENT_BODY);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null on InterruptedException")
        void shouldReturnNullOnInterruptedException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new InterruptedException("Interrupted"));

            Long result = service.postComment(REPO_URL, PR_NUMBER, COMMENT_BODY);

            assertThat(result).isNull();
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Nested
    @DisplayName("buildRequestBody")
    class BuildRequestBody {

        @Test
        @DisplayName("Should build valid JSON request body")
        void shouldBuildValidJson() {
            String body = service.buildRequestBody("Hello **world**");
            assertThat(body).contains("\"body\"");
            assertThat(body).contains("Hello **world**");
        }
    }

    @Nested
    @DisplayName("parseCommentId")
    class ParseCommentId {

        @Test
        @DisplayName("Should extract comment ID from response")
        void shouldExtractId() {
            Long id = service.parseCommentId("{\"id\": 999, \"body\": \"test\"}");
            assertThat(id).isEqualTo(999L);
        }

        @Test
        @DisplayName("Should return null for invalid JSON")
        void shouldReturnNullForInvalidJson() {
            Long id = service.parseCommentId("not json");
            assertThat(id).isNull();
        }

        @Test
        @DisplayName("Should return null when id field missing")
        void shouldReturnNullWhenNoId() {
            Long id = service.parseCommentId("{\"body\": \"test\"}");
            assertThat(id).isNull();
        }
    }
}
