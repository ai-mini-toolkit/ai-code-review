package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CommitStatusResponseDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import com.aicodereview.common.dto.threshold.ThresholdViolationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitLabCommitStatusServiceImpl.
 *
 * @since 6.4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GitLabCommitStatusServiceImpl Unit Tests")
class GitLabCommitStatusServiceImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GitLabCommitStatusServiceImpl service;

    private static final String REPO_URL = "https://gitlab.com/test/repo";
    private static final String COMMIT_HASH = "abc123def456";

    @BeforeEach
    void setUp() {
        service = new GitLabCommitStatusServiceImpl(httpClient, "test-gitlab-token", "https://gitlab.com");
    }

    private ReviewStatisticsDTO buildStats(int total, Map<String, Integer> bySeverity) {
        return ReviewStatisticsDTO.builder()
                .total(total)
                .bySeverity(bySeverity)
                .byCategory(Map.of())
                .build();
    }

    private Map<String, Integer> severityMap(int critical, int high, int medium, int low) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("CRITICAL", critical);
        map.put("HIGH", high);
        map.put("MEDIUM", medium);
        map.put("LOW", low);
        return map;
    }

    @Nested
    @DisplayName("mapState")
    class MapState {

        @Test
        @DisplayName("Should return 'success' when threshold passed")
        void shouldReturnSuccessWhenPassed() {
            ThresholdValidationResultDTO result = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            assertThat(service.mapState(result)).isEqualTo("success");
        }

        @Test
        @DisplayName("Should return 'failed' when BLOCK_MERGE action")
        void shouldReturnFailedForBlockMerge() {
            ThresholdValidationResultDTO result = ThresholdValidationResultDTO.builder()
                    .passed(false).action("BLOCK_MERGE").build();

            assertThat(service.mapState(result)).isEqualTo("failed");
        }

        @Test
        @DisplayName("Should return 'success' when WARN_ONLY action")
        void shouldReturnSuccessForWarnOnly() {
            ThresholdValidationResultDTO result = ThresholdValidationResultDTO.builder()
                    .passed(false).action("WARN_ONLY").build();

            assertThat(service.mapState(result)).isEqualTo("success");
        }
    }

    @Nested
    @DisplayName("buildDescription")
    class BuildDescription {

        @Test
        @DisplayName("Should build passed description with issue count")
        void shouldBuildPassedDescription() {
            ReviewStatisticsDTO stats = buildStats(3, severityMap(0, 1, 1, 1));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            String desc = service.buildDescription(stats, threshold);

            assertThat(desc).contains("passed");
            assertThat(desc).contains("3 issues found");
        }

        @Test
        @DisplayName("Should build failed description with violation count")
        void shouldBuildFailedDescription() {
            ReviewStatisticsDTO stats = buildStats(5, severityMap(2, 3, 0, 0));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(false)
                    .violations(List.of(
                            ThresholdViolationDTO.builder()
                                    .rule("CRITICAL <= 0").actual(2).threshold(0).build()))
                    .action("BLOCK_MERGE")
                    .build();

            String desc = service.buildDescription(stats, threshold);

            assertThat(desc).contains("failed");
            assertThat(desc).contains("1 threshold violations");
        }

        @Test
        @DisplayName("Should build warning description for WARN_ONLY")
        void shouldBuildWarningDescription() {
            ReviewStatisticsDTO stats = buildStats(10, severityMap(0, 5, 3, 2));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(false)
                    .violations(List.of(
                            ThresholdViolationDTO.builder()
                                    .rule("HIGH <= 3").actual(5).threshold(3).build()))
                    .action("WARN_ONLY")
                    .build();

            String desc = service.buildDescription(stats, threshold);

            assertThat(desc).contains("warning");
            assertThat(desc).contains("1 threshold violations");
            assertThat(desc).contains("10 issues found");
        }

        @Test
        @DisplayName("Should keep description within 255 character limit")
        void shouldKeepDescriptionWithinLimit() {
            // Even with extreme values, description format stays under 255 chars
            ReviewStatisticsDTO stats = buildStats(Integer.MAX_VALUE, severityMap(0, 0, 0, 0));
            List<ThresholdViolationDTO> manyViolations = new java.util.ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                manyViolations.add(ThresholdViolationDTO.builder()
                        .rule("RULE_" + i).actual(i).threshold(0).build());
            }
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(false).violations(manyViolations).action("WARN_ONLY").build();

            String desc = service.buildDescription(stats, threshold);

            assertThat(desc.length()).isLessThanOrEqualTo(255);
        }
    }

    @Nested
    @DisplayName("buildRequestBody")
    class BuildRequestBody {

        @Test
        @DisplayName("Should produce JSON with state, name, and description fields")
        void shouldProduceCorrectJsonFields() {
            String body = service.buildRequestBody("success", "AI Code Review passed");

            assertThat(body).contains("\"state\":\"success\"");
            assertThat(body).contains("\"name\":\"AI Code Review\"");
            assertThat(body).contains("\"description\":\"AI Code Review passed\"");
        }

        @Test
        @DisplayName("Should produce valid JSON for failed state")
        void shouldProduceValidJsonForFailedState() {
            String body = service.buildRequestBody("failed", "AI Code Review failed — 2 threshold violations");

            assertThat(body).contains("\"state\":\"failed\"");
            assertThat(body).contains("\"name\":\"AI Code Review\"");
            assertThat(body).contains("threshold violations");
        }
    }

    @Nested
    @DisplayName("parseResponse error handling")
    class ParseResponseErrorHandling {

        @Test
        @DisplayName("Should return minimal result when API returns malformed JSON")
        void shouldReturnMinimalResultOnMalformedJson() throws Exception {
            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("not-valid-json{{{");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            CommitStatusResponseDTO result = service.updateCommitStatus(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo("success");
            assertThat(result.getId()).isNull();
        }
    }

    @Nested
    @DisplayName("updateCommitStatus")
    class UpdateCommitStatus {

        @Test
        @DisplayName("Should update commit status and return response on success")
        void shouldUpdateCommitStatusSuccessfully() throws Exception {
            ReviewStatisticsDTO stats = buildStats(2, severityMap(0, 1, 1, 0));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn(
                    "{\"id\":12345,\"status\":\"success\",\"description\":\"AI Code Review passed\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            CommitStatusResponseDTO result = service.updateCommitStatus(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(12345L);
            assertThat(result.getState()).isEqualTo("success");

            // Verify request details
            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());
            HttpRequest sentRequest = requestCaptor.getValue();
            assertThat(sentRequest.uri().toString())
                    .contains("/api/v4/projects/")
                    .contains("/statuses/" + COMMIT_HASH);
            assertThat(sentRequest.method()).isEqualTo("POST");
            assertThat(sentRequest.headers().firstValue("Content-Type"))
                    .isPresent().hasValue("application/json");
            assertThat(sentRequest.headers().firstValue("PRIVATE-TOKEN"))
                    .isPresent().hasValue("test-gitlab-token");
        }

        @Test
        @DisplayName("Should return null when token is empty")
        void shouldReturnNullWhenTokenEmpty() {
            GitLabCommitStatusServiceImpl noTokenService =
                    new GitLabCommitStatusServiceImpl(httpClient, "", "https://gitlab.com");

            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            CommitStatusResponseDTO result = noTokenService.updateCommitStatus(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNull();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should return null when token is null")
        void shouldReturnNullWhenTokenNull() {
            GitLabCommitStatusServiceImpl noTokenService =
                    new GitLabCommitStatusServiceImpl(httpClient, null, "https://gitlab.com");

            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            CommitStatusResponseDTO result = noTokenService.updateCommitStatus(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNull();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should return null on API error (non-2xx)")
        void shouldReturnNullOnApiError() throws Exception {
            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            when(httpResponse.statusCode()).thenReturn(403);
            when(httpResponse.body()).thenReturn("{\"message\":\"Forbidden\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            CommitStatusResponseDTO result = service.updateCommitStatus(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null on IOException")
        void shouldReturnNullOnIOException() throws Exception {
            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new java.io.IOException("Connection refused"));

            CommitStatusResponseDTO result = service.updateCommitStatus(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null on InterruptedException and restore interrupt flag")
        void shouldReturnNullOnInterruptedException() throws Exception {
            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new InterruptedException("Thread interrupted"));

            CommitStatusResponseDTO result = service.updateCommitStatus(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNull();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // Clear interrupt flag for test cleanup
            Thread.interrupted();
        }

        @Test
        @DisplayName("Should send failed state for BLOCK_MERGE threshold violation")
        void shouldSendFailedStateForBlockMerge() throws Exception {
            ReviewStatisticsDTO stats = buildStats(5, severityMap(2, 3, 0, 0));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(false)
                    .violations(List.of(
                            ThresholdViolationDTO.builder()
                                    .rule("CRITICAL <= 0").actual(2).threshold(0).build()))
                    .action("BLOCK_MERGE")
                    .build();

            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\":99,\"status\":\"failed\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            CommitStatusResponseDTO result = service.updateCommitStatus(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo("failed");
        }

        @Test
        @DisplayName("Should use configurable base URL for self-hosted GitLab")
        void shouldUseConfigurableBaseUrl() throws Exception {
            GitLabCommitStatusServiceImpl selfHostedService =
                    new GitLabCommitStatusServiceImpl(httpClient, "token", "https://gitlab.example.com");

            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\":1}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            selfHostedService.updateCommitStatus(REPO_URL, COMMIT_HASH, stats, threshold);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue().uri().toString())
                    .startsWith("https://gitlab.example.com/api/v4/projects/");
        }
    }

    @Nested
    @DisplayName("parseProjectPath")
    class ParseProjectPath {

        @Test
        @DisplayName("Should parse standard GitLab URL")
        void shouldParseStandardUrl() {
            assertThat(service.parseProjectPath("https://gitlab.com/owner/repo"))
                    .isEqualTo("owner%2Frepo");
        }

        @Test
        @DisplayName("Should parse URL with .git suffix")
        void shouldParseUrlWithGitSuffix() {
            assertThat(service.parseProjectPath("https://gitlab.com/owner/repo.git"))
                    .isEqualTo("owner%2Frepo");
        }

        @Test
        @DisplayName("Should parse URL with nested groups")
        void shouldParseUrlWithNestedGroups() {
            assertThat(service.parseProjectPath("https://gitlab.com/group/subgroup/repo"))
                    .isEqualTo("group%2Fsubgroup%2Frepo");
        }
    }
}
