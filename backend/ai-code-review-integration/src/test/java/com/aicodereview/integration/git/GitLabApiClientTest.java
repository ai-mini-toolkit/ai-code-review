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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GitLabApiClient Tests")
@ExtendWith(MockitoExtension.class)
class GitLabApiClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GitLabApiClient client;

    @BeforeEach
    void setUp() {
        client = new GitLabApiClient(httpClient, "test-token", "https://gitlab.com");
    }

    @Nested
    @DisplayName("getPlatform")
    class GetPlatform {

        @Test
        @DisplayName("Should return GITLAB")
        void shouldReturnGitLab() {
            assertThat(client.getPlatform()).isEqualTo(GitPlatform.GITLAB);
        }
    }

    @Nested
    @DisplayName("parseProjectPath")
    class ParseProjectPath {

        @Test
        @DisplayName("Should URL-encode project path")
        void shouldUrlEncodeProjectPath() {
            String result = client.parseProjectPath("https://gitlab.com/namespace/project");
            assertThat(result).isEqualTo("namespace%2Fproject");
        }

        @Test
        @DisplayName("Should handle nested groups")
        void shouldHandleNestedGroups() {
            String result = client.parseProjectPath("https://gitlab.com/group/subgroup/project");
            assertThat(result).isEqualTo("group%2Fsubgroup%2Fproject");
        }

        @Test
        @DisplayName("Should strip .git suffix")
        void shouldStripGitSuffix() {
            String result = client.parseProjectPath("https://gitlab.com/ns/proj.git");
            assertThat(result).isEqualTo("ns%2Fproj");
        }

        @Test
        @DisplayName("Should throw for null URL")
        void shouldThrowForNull() {
            assertThatThrownBy(() -> client.parseProjectPath(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getFileContent")
    class GetFileContent {

        @Test
        @DisplayName("Should call correct GitLab file raw URL")
        void shouldCallFileRawUrl() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("file content");
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getFileContent(
                    "https://gitlab.com/ns/proj", "abc123", "src/App.java");
            assertThat(result).isEqualTo("file content");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            String url = captor.getValue().uri().toString();
            assertThat(url).contains("/api/v4/projects/ns%2Fproj/repository/files/");
            assertThat(url).contains("ref=abc123");
            assertThat(captor.getValue().headers().firstValue("PRIVATE-TOKEN"))
                    .hasValue("test-token");
        }
    }

    @Nested
    @DisplayName("getDiff (commit)")
    class GetCommitDiff {

        @Test
        @DisplayName("Should call commit diff URL and assemble unified diff")
        void shouldAssembleUnifiedDiff() throws Exception {
            String gitlabResponse = "[{" +
                    "\"old_path\":\"src/App.java\"," +
                    "\"new_path\":\"src/App.java\"," +
                    "\"diff\":\"@@ -1,3 +1,4 @@\\n class App {\\n+    int x;\\n }\\n\"," +
                    "\"new_file\":false," +
                    "\"renamed_file\":false," +
                    "\"deleted_file\":false" +
                    "}]";

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(gitlabResponse);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://gitlab.com/ns/proj", "sha123");

            assertThat(result).contains("diff --git a/src/App.java b/src/App.java");
            assertThat(result).contains("--- a/src/App.java");
            assertThat(result).contains("+++ b/src/App.java");
            assertThat(result).contains("@@ -1,3 +1,4 @@");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            assertThat(captor.getValue().uri().toString())
                    .contains("/api/v4/projects/ns%2Fproj/repository/commits/sha123/diff");
        }

        @Test
        @DisplayName("Should handle new file in diff assembly")
        void shouldHandleNewFile() throws Exception {
            String gitlabResponse = "[{" +
                    "\"old_path\":\"New.java\"," +
                    "\"new_path\":\"New.java\"," +
                    "\"diff\":\"@@ -0,0 +1 @@\\n+new\\n\"," +
                    "\"new_file\":true," +
                    "\"renamed_file\":false," +
                    "\"deleted_file\":false" +
                    "}]";

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(gitlabResponse);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://gitlab.com/ns/proj", "sha");

            assertThat(result).contains("new file mode 100644");
            assertThat(result).contains("--- /dev/null");
            assertThat(result).contains("+++ b/New.java");
        }

        @Test
        @DisplayName("Should handle deleted file in diff assembly")
        void shouldHandleDeletedFile() throws Exception {
            String gitlabResponse = "[{" +
                    "\"old_path\":\"Old.java\"," +
                    "\"new_path\":\"Old.java\"," +
                    "\"diff\":\"@@ -1 +0,0 @@\\n-old\\n\"," +
                    "\"new_file\":false," +
                    "\"renamed_file\":false," +
                    "\"deleted_file\":true" +
                    "}]";

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(gitlabResponse);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://gitlab.com/ns/proj", "sha");

            assertThat(result).contains("deleted file mode 100644");
            assertThat(result).contains("--- a/Old.java");
            assertThat(result).contains("+++ /dev/null");
        }

        @Test
        @DisplayName("Should handle renamed file in diff assembly")
        void shouldHandleRenamedFile() throws Exception {
            String gitlabResponse = "[{" +
                    "\"old_path\":\"old.java\"," +
                    "\"new_path\":\"new.java\"," +
                    "\"diff\":\"\"," +
                    "\"new_file\":false," +
                    "\"renamed_file\":true," +
                    "\"deleted_file\":false" +
                    "}]";

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(gitlabResponse);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://gitlab.com/ns/proj", "sha");

            assertThat(result).contains("rename from old.java");
            assertThat(result).contains("rename to new.java");
        }
    }

    @Nested
    @DisplayName("getDiff (branch comparison)")
    class GetBranchDiff {

        @Test
        @DisplayName("Should call compare URL and extract diffs array")
        void shouldCallCompareUrl() throws Exception {
            String compareResponse = "{\"diffs\":[{" +
                    "\"old_path\":\"f.java\",\"new_path\":\"f.java\"," +
                    "\"diff\":\"@@ -1 +1 @@\\n-old\\n+new\\n\"," +
                    "\"new_file\":false,\"renamed_file\":false,\"deleted_file\":false" +
                    "}],\"commits\":[]}";

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(compareResponse);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://gitlab.com/ns/proj", "main", "feature");
            assertThat(result).contains("diff --git a/f.java b/f.java");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            String url = captor.getValue().uri().toString();
            assertThat(url).contains("/repository/compare?from=main&to=feature");
        }

        @Test
        @DisplayName("Should URL-encode branch names with special characters")
        void shouldUrlEncodeBranchNames() throws Exception {
            String compareResponse = "{\"diffs\":[],\"commits\":[]}";

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(compareResponse);
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            client.getDiff("https://gitlab.com/ns/proj", "release/1.0", "feature/my&branch");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            String url = captor.getValue().uri().toString();
            // Branch names should be URL-encoded in query parameters
            assertThat(url).contains("from=release%2F1.0");
            assertThat(url).contains("to=feature%2Fmy%26branch");
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

            assertThatThrownBy(() -> client.getFileContent(
                    "https://gitlab.com/ns/proj", "sha", "missing.txt"))
                    .isInstanceOf(GitApiException.class)
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("Should retry on 500 and succeed")
        void shouldRetryOn500() throws Exception {
            HttpResponse<String> failResp = mock(HttpResponse.class);
            when(failResp.statusCode()).thenReturn(500);

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("[]");

            doReturn(failResp).doReturn(httpResponse)
                    .when(httpClient).send(any(HttpRequest.class), any());

            String result = client.getDiff("https://gitlab.com/ns/proj", "sha");
            assertThat(result).isEmpty();
            verify(httpClient, times(2)).send(any(), any());
        }
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("Should use PRIVATE-TOKEN header")
        void shouldUsePrivateTokenHeader() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("content");
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            client.getFileContent("https://gitlab.com/ns/proj", "sha", "f.txt");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            assertThat(captor.getValue().headers().firstValue("PRIVATE-TOKEN"))
                    .hasValue("test-token");
        }

        @Test
        @DisplayName("Should not set PRIVATE-TOKEN when token is empty")
        void shouldNotSetTokenWhenEmpty() throws Exception {
            GitLabApiClient noTokenClient = new GitLabApiClient(httpClient, "", "https://gitlab.com");
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("content");
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            noTokenClient.getFileContent("https://gitlab.com/ns/proj", "sha", "f.txt");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            assertThat(captor.getValue().headers().firstValue("PRIVATE-TOKEN")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Self-hosted GitLab")
    class SelfHosted {

        @Test
        @DisplayName("Should use custom base URL")
        void shouldUseCustomBaseUrl() throws Exception {
            GitLabApiClient customClient = new GitLabApiClient(
                    httpClient, "token", "https://gitlab.corp.com");

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("content");
            doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

            customClient.getFileContent("https://gitlab.corp.com/ns/proj", "sha", "f.txt");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            assertThat(captor.getValue().uri().toString())
                    .startsWith("https://gitlab.corp.com/api/v4/");
        }
    }
}
