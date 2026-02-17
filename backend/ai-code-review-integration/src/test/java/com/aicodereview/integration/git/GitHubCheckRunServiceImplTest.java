package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CheckRunOutputDTO;
import com.aicodereview.common.dto.checkrun.CheckRunResponseDTO;
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
 * Unit tests for GitHubCheckRunServiceImpl.
 *
 * @since 6.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubCheckRunServiceImpl Unit Tests")
class GitHubCheckRunServiceImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GitHubCheckRunServiceImpl service;

    private static final String REPO_URL = "https://github.com/test/repo";
    private static final String COMMIT_HASH = "abc123def456";

    @BeforeEach
    void setUp() {
        service = new GitHubCheckRunServiceImpl(httpClient, "test-github-token");
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
    @DisplayName("mapConclusion")
    class MapConclusion {

        @Test
        @DisplayName("Should return 'success' when threshold passed")
        void shouldReturnSuccessWhenPassed() {
            ThresholdValidationResultDTO result = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            assertThat(service.mapConclusion(result)).isEqualTo("success");
        }

        @Test
        @DisplayName("Should return 'failure' when BLOCK_MERGE action")
        void shouldReturnFailureForBlockMerge() {
            ThresholdValidationResultDTO result = ThresholdValidationResultDTO.builder()
                    .passed(false).action("BLOCK_MERGE").build();

            assertThat(service.mapConclusion(result)).isEqualTo("failure");
        }

        @Test
        @DisplayName("Should return 'neutral' when WARN_ONLY action")
        void shouldReturnNeutralForWarnOnly() {
            ThresholdValidationResultDTO result = ThresholdValidationResultDTO.builder()
                    .passed(false).action("WARN_ONLY").build();

            assertThat(service.mapConclusion(result)).isEqualTo("neutral");
        }
    }

    @Nested
    @DisplayName("buildOutput")
    class BuildOutput {

        @Test
        @DisplayName("Should build passed output with correct title")
        void shouldBuildPassedOutput() {
            ReviewStatisticsDTO stats = buildStats(3, severityMap(0, 1, 1, 1));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            CheckRunOutputDTO output = service.buildOutput(stats, threshold);

            assertThat(output.getTitle()).contains("Passed");
            assertThat(output.getSummary()).contains("Total Issues:** 3");
            assertThat(output.getSummary()).contains("HIGH");
        }

        @Test
        @DisplayName("Should build failed output with violations")
        void shouldBuildFailedOutputWithViolations() {
            ReviewStatisticsDTO stats = buildStats(5, severityMap(2, 3, 0, 0));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(false)
                    .violations(List.of(
                            ThresholdViolationDTO.builder()
                                    .rule("CRITICAL <= 0").actual(2).threshold(0).build()
                    ))
                    .action("BLOCK_MERGE")
                    .build();

            CheckRunOutputDTO output = service.buildOutput(stats, threshold);

            assertThat(output.getTitle()).contains("Failed");
            assertThat(output.getSummary()).contains("Threshold Violations");
            assertThat(output.getSummary()).contains("CRITICAL <= 0");
            assertThat(output.getSummary()).contains("BLOCK_MERGE");
        }

        @Test
        @DisplayName("Should skip zero-count severities in table")
        void shouldSkipZeroCountSeverities() {
            ReviewStatisticsDTO stats = buildStats(2, severityMap(0, 2, 0, 0));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            CheckRunOutputDTO output = service.buildOutput(stats, threshold);

            assertThat(output.getSummary()).contains("HIGH");
            assertThat(output.getSummary()).doesNotContain("| CRITICAL |");
            assertThat(output.getSummary()).doesNotContain("| LOW |");
        }

        @Test
        @DisplayName("Should truncate summary when exceeding 65535 characters")
        void shouldTruncateLongSummary() {
            // Build a stats map with many entries to generate a very long summary
            Map<String, Integer> largeSeverityMap = new LinkedHashMap<>();
            for (int i = 0; i < 10000; i++) {
                largeSeverityMap.put("SEVERITY_" + String.format("%05d", i), i + 1);
            }
            ReviewStatisticsDTO stats = buildStats(50000, largeSeverityMap);

            List<ThresholdViolationDTO> manyViolations = new java.util.ArrayList<>();
            for (int i = 0; i < 5000; i++) {
                manyViolations.add(ThresholdViolationDTO.builder()
                        .rule("RULE_" + i + " <= 0").actual(i + 1).threshold(0).build());
            }
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(false).violations(manyViolations).action("BLOCK_MERGE").build();

            CheckRunOutputDTO output = service.buildOutput(stats, threshold);

            assertThat(output.getSummary().length()).isLessThanOrEqualTo(65535);
            assertThat(output.getSummary()).endsWith("...");
        }
    }

    @Nested
    @DisplayName("createCompletedCheckRun")
    class CreateCompletedCheckRun {

        @Test
        @DisplayName("Should create check run and return response on success")
        void shouldCreateCheckRunSuccessfully() throws Exception {
            ReviewStatisticsDTO stats = buildStats(2, severityMap(0, 1, 1, 0));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn(
                    "{\"id\":12345,\"html_url\":\"https://github.com/test/repo/runs/12345\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            CheckRunResponseDTO result = service.createCompletedCheckRun(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(12345L);
            assertThat(result.getHtmlUrl()).isEqualTo("https://github.com/test/repo/runs/12345");
            assertThat(result.getConclusion()).isEqualTo("success");

            // Verify request was sent
            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());
            HttpRequest sentRequest = requestCaptor.getValue();
            assertThat(sentRequest.uri().toString())
                    .isEqualTo("https://api.github.com/repos/test/repo/check-runs");
            assertThat(sentRequest.method()).isEqualTo("POST");
            assertThat(sentRequest.headers().firstValue("Content-Type"))
                    .isPresent().hasValue("application/json");
            assertThat(sentRequest.headers().firstValue("Accept"))
                    .isPresent().hasValue("application/vnd.github+json");
        }

        @Test
        @DisplayName("Should return null when token is empty")
        void shouldReturnNullWhenTokenEmpty() {
            GitHubCheckRunServiceImpl noTokenService =
                    new GitHubCheckRunServiceImpl(httpClient, "");

            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            CheckRunResponseDTO result = noTokenService.createCompletedCheckRun(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNull();
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("Should return null when token is null")
        void shouldReturnNullWhenTokenNull() {
            GitHubCheckRunServiceImpl noTokenService =
                    new GitHubCheckRunServiceImpl(httpClient, null);

            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            CheckRunResponseDTO result = noTokenService.createCompletedCheckRun(
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

            CheckRunResponseDTO result = service.createCompletedCheckRun(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null on IOException (no exception propagation)")
        void shouldReturnNullOnIOException() throws Exception {
            ReviewStatisticsDTO stats = buildStats(0, Map.of());
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(true).action(null).build();

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new java.io.IOException("Connection refused"));

            CheckRunResponseDTO result = service.createCompletedCheckRun(
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

            CheckRunResponseDTO result = service.createCompletedCheckRun(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNull();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // Clear interrupt flag for test cleanup
            Thread.interrupted();
        }

        @Test
        @DisplayName("Should send failure conclusion for BLOCK_MERGE threshold violation")
        void shouldSendFailureConclusionForBlockMerge() throws Exception {
            ReviewStatisticsDTO stats = buildStats(5, severityMap(2, 3, 0, 0));
            ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                    .passed(false)
                    .violations(List.of(
                            ThresholdViolationDTO.builder()
                                    .rule("CRITICAL <= 0").actual(2).threshold(0).build()))
                    .action("BLOCK_MERGE")
                    .build();

            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\":99,\"html_url\":\"https://github.com/x\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            CheckRunResponseDTO result = service.createCompletedCheckRun(
                    REPO_URL, COMMIT_HASH, stats, threshold);

            assertThat(result).isNotNull();
            assertThat(result.getConclusion()).isEqualTo("failure");
        }
    }

    @Nested
    @DisplayName("parseOwnerRepo")
    class ParseOwnerRepo {

        @Test
        @DisplayName("Should parse standard GitHub URL")
        void shouldParseStandardUrl() {
            assertThat(service.parseOwnerRepo("https://github.com/owner/repo"))
                    .isEqualTo("owner/repo");
        }

        @Test
        @DisplayName("Should parse URL with .git suffix")
        void shouldParseUrlWithGitSuffix() {
            assertThat(service.parseOwnerRepo("https://github.com/owner/repo.git"))
                    .isEqualTo("owner/repo");
        }
    }
}
