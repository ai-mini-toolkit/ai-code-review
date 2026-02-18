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
 * Unit tests for GitLabMRCommentServiceImpl.
 *
 * @since 7.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GitLabMRCommentServiceImpl Unit Tests")
class GitLabMRCommentServiceImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GitLabMRCommentServiceImpl service;

    private static final String REPO_URL = "https://gitlab.com/group/project";
    private static final Integer MR_IID = 10;
    private static final String COMMENT_BODY = "## AI Code Review\n\nAll checks passed.";

    @BeforeEach
    void setUp() {
        service = new GitLabMRCommentServiceImpl(httpClient, "test-token", "https://gitlab.com");
    }

    @Nested
    @DisplayName("postComment")
    class PostComment {

        @Test
        @DisplayName("Should post MR note successfully and return note ID")
        void shouldPostNoteSuccessfully() throws Exception {
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 67890, \"body\": \"AI review\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            Long noteId = service.postComment(REPO_URL, MR_IID, COMMENT_BODY);

            assertThat(noteId).isEqualTo(67890L);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());
            HttpRequest request = requestCaptor.getValue();
            assertThat(request.uri().toString())
                    .isEqualTo("https://gitlab.com/api/v4/projects/group%2Fproject/merge_requests/10/notes");
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.headers().firstValue("PRIVATE-TOKEN")).hasValue("test-token");
        }

        @Test
        @DisplayName("Should skip when token not configured")
        void shouldSkipWhenNoToken() {
            GitLabMRCommentServiceImpl noTokenService =
                    new GitLabMRCommentServiceImpl(httpClient, "", "https://gitlab.com");

            Long result = noTokenService.postComment(REPO_URL, MR_IID, COMMENT_BODY);

            assertThat(result).isNull();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should return null on HTTP error response")
        void shouldReturnNullOnHttpError() throws Exception {
            when(httpResponse.statusCode()).thenReturn(404);
            when(httpResponse.body()).thenReturn("{\"error\": \"Not Found\"}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            Long result = service.postComment(REPO_URL, MR_IID, COMMENT_BODY);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null on IOException")
        void shouldReturnNullOnIOException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new IOException("Connection refused"));

            Long result = service.postComment(REPO_URL, MR_IID, COMMENT_BODY);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should use custom base URL")
        void shouldUseCustomBaseUrl() throws Exception {
            GitLabMRCommentServiceImpl customService =
                    new GitLabMRCommentServiceImpl(httpClient, "token", "https://gitlab.example.com/");

            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 111}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            customService.postComment(REPO_URL, MR_IID, COMMENT_BODY);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue().uri().toString())
                    .startsWith("https://gitlab.example.com/api/v4/projects/");
        }
    }

    @Nested
    @DisplayName("parseNoteId")
    class ParseNoteId {

        @Test
        @DisplayName("Should extract note ID from response")
        void shouldExtractId() {
            Long id = service.parseNoteId("{\"id\": 555, \"body\": \"test\"}");
            assertThat(id).isEqualTo(555L);
        }

        @Test
        @DisplayName("Should return null for invalid JSON")
        void shouldReturnNullForInvalidJson() {
            Long id = service.parseNoteId("not json");
            assertThat(id).isNull();
        }
    }
}
