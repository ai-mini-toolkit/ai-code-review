package com.aicodereview.service;

import com.aicodereview.common.dto.DiffMetadata;
import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.FileDiffInfo;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.FileInfo;
import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.Language;
import com.aicodereview.common.enums.TaskType;
import com.aicodereview.common.exception.GitApiException;
import com.aicodereview.integration.git.GitPlatformClient;
import com.aicodereview.integration.git.GitPlatformClientFactory;
import com.aicodereview.repository.entity.ReviewTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ReviewContextAssembler Tests")
@ExtendWith(MockitoExtension.class)
class ReviewContextAssemblerTest {

    @Mock
    private GitPlatformClientFactory clientFactory;

    @Mock
    private GitPlatformClient gitClient;

    @Mock
    private DiffMetadataExtractor diffExtractor;

    private ReviewContextAssembler assembler;

    private static final String REPO_URL = "https://github.com/owner/repo";
    private static final String COMMIT_HASH = "abc123def456";
    private static final String SAMPLE_DIFF = "diff --git a/src/App.java b/src/App.java\n" +
            "--- a/src/App.java\n+++ b/src/App.java\n@@ -1,3 +1,4 @@\n class App {\n+    int x;\n }\n";

    @BeforeEach
    void setUp() {
        assembler = new ReviewContextAssembler(clientFactory, diffExtractor);
        ReflectionTestUtils.setField(assembler, "maxContextTokens", 100000);
        ReflectionTestUtils.setField(assembler, "maxFileTokens", 10000);
        ReflectionTestUtils.setField(assembler, "maxFiles", 50);
    }

    private ReviewTask buildTask() {
        return ReviewTask.builder()
                .id(1L)
                .repoUrl(REPO_URL)
                .commitHash(COMMIT_HASH)
                .branch("main")
                .author("testuser")
                .taskType(TaskType.PUSH)
                .build();
    }

    private ReviewTask buildPrTask() {
        return ReviewTask.builder()
                .id(2L)
                .repoUrl(REPO_URL)
                .commitHash(COMMIT_HASH)
                .branch("feature/test")
                .author("pruser")
                .taskType(TaskType.PULL_REQUEST)
                .prNumber(42)
                .prTitle("Add feature X")
                .prDescription("This PR adds feature X")
                .build();
    }

    private DiffMetadata buildMetadata(List<FileDiffInfo> files) {
        return DiffMetadata.builder()
                .files(files)
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(files.size())
                        .totalLinesAdded(10)
                        .totalLinesDeleted(5)
                        .build())
                .build();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("Should assemble context with diff, metadata, and file contents")
        void shouldAssembleCompleteContext() {
            ReviewTask task = buildTask();
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder()
                            .oldPath("src/App.java").newPath("src/App.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(1).linesDeleted(0).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(SAMPLE_DIFF).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(SAMPLE_DIFF)).thenReturn(metadata);
            doReturn("public class App { int x; }").when(gitClient)
                    .getFileContent(REPO_URL, COMMIT_HASH, "src/App.java");

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getRawDiff()).isEqualTo(SAMPLE_DIFF);
            assertThat(result.getFiles()).hasSize(1);
            assertThat(result.getFiles().get(0).getPath()).isEqualTo("src/App.java");
            assertThat(result.getFiles().get(0).getChangeType()).isEqualTo(ChangeType.MODIFY);
            assertThat(result.getFileContents()).containsKey("src/App.java");
            assertThat(result.getStatistics().getTotalFilesChanged()).isEqualTo(1);
            assertThat(result.getTaskMeta().getAuthor()).isEqualTo("testuser");
            assertThat(result.getTaskMeta().getBranch()).isEqualTo("main");
            assertThat(result.getTaskMeta().getCommitHash()).isEqualTo(COMMIT_HASH);
            assertThat(result.getTaskMeta().getTaskType()).isEqualTo(TaskType.PUSH);
        }

        @Test
        @DisplayName("Should assemble context with multiple files")
        void shouldHandleMultipleFiles() {
            ReviewTask task = buildTask();
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder()
                            .oldPath("a.java").newPath("a.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(3).linesDeleted(1).build(),
                    FileDiffInfo.builder()
                            .newPath("b.java")
                            .changeType(ChangeType.ADD).language(Language.JAVA)
                            .isBinary(false).linesAdded(5).linesDeleted(0).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(SAMPLE_DIFF).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(SAMPLE_DIFF)).thenReturn(metadata);
            doReturn("content a").when(gitClient).getFileContent(REPO_URL, COMMIT_HASH, "a.java");
            doReturn("content b").when(gitClient).getFileContent(REPO_URL, COMMIT_HASH, "b.java");

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getFileContents()).hasSize(2);
            assertThat(result.getFiles()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("TaskMetadata Extraction")
    class TaskMetadataExtraction {

        @Test
        @DisplayName("Should extract PR metadata from PULL_REQUEST task")
        void shouldExtractPrMetadata() {
            ReviewTask task = buildPrTask();
            DiffMetadata metadata = buildMetadata(List.of());

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn("").when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata("")).thenReturn(metadata);

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getTaskMeta().getPrTitle()).isEqualTo("Add feature X");
            assertThat(result.getTaskMeta().getPrDescription()).isEqualTo("This PR adds feature X");
            assertThat(result.getTaskMeta().getTaskType()).isEqualTo(TaskType.PULL_REQUEST);
        }

        @Test
        @DisplayName("Should handle PUSH task with null PR fields")
        void shouldHandleNullPrFields() {
            ReviewTask task = buildTask();
            DiffMetadata metadata = buildMetadata(List.of());

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn("").when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata("")).thenReturn(metadata);

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getTaskMeta().getPrTitle()).isNull();
            assertThat(result.getTaskMeta().getPrDescription()).isNull();
            assertThat(result.getTaskMeta().getTaskType()).isEqualTo(TaskType.PUSH);
        }
    }

    @Nested
    @DisplayName("Truncation")
    class Truncation {

        @Test
        @DisplayName("Should truncate raw diff exceeding token limit")
        void shouldTruncateOversizedDiff() {
            ReflectionTestUtils.setField(assembler, "maxContextTokens", 25); // 100 chars max
            String largeDiff = "x".repeat(200);

            String result = assembler.truncateRawDiff(largeDiff);

            assertThat(result.length()).isLessThanOrEqualTo(100);
            assertThat(result).contains("[TRUNCATED:");
        }

        @Test
        @DisplayName("Should not truncate diff within token limit")
        void shouldNotTruncateSmallDiff() {
            String smallDiff = "small diff content";
            String result = assembler.truncateRawDiff(smallDiff);
            assertThat(result).isEqualTo(smallDiff);
        }

        @Test
        @DisplayName("Should truncate individual file exceeding per-file token limit")
        void shouldTruncateOversizedFile() {
            ReflectionTestUtils.setField(assembler, "maxFileTokens", 25); // 100 chars max
            Map<String, String> contents = Map.of("big.java", "y".repeat(200));

            // remainingTokens set high so per-file limit is the binding constraint
            Map<String, String> result = assembler.truncateFileContents(contents, 100000);

            assertThat(result.get("big.java").length()).isLessThanOrEqualTo(100);
            assertThat(result.get("big.java")).contains("[TRUNCATED:");
        }

        @Test
        @DisplayName("Should not truncate file within per-file token limit")
        void shouldNotTruncateSmallFile() {
            Map<String, String> contents = Map.of("small.java", "content");

            Map<String, String> result = assembler.truncateFileContents(contents, 100000);

            assertThat(result.get("small.java")).isEqualTo("content");
        }

        @Test
        @DisplayName("Should handle null and empty diff in truncation")
        void shouldHandleNullDiff() {
            assertThat(assembler.truncateRawDiff(null)).isNull();
            assertThat(assembler.truncateRawDiff("")).isEmpty();
        }

        @Test
        @DisplayName("Should not crash with very small token limit (M1 fix)")
        void shouldHandleVerySmallTokenLimit() {
            ReflectionTestUtils.setField(assembler, "maxContextTokens", 5); // 20 chars max
            String largeDiff = "x".repeat(200);

            // Should not throw StringIndexOutOfBoundsException
            String result = assembler.truncateRawDiff(largeDiff);

            // Too small for marker, just hard-truncated to 20 chars
            assertThat(result).hasSize(20);
            assertThat(result).isEqualTo("x".repeat(20));
        }

        @Test
        @DisplayName("Should not crash with very small per-file token limit (M1 fix)")
        void shouldHandleVerySmallFileTokenLimit() {
            ReflectionTestUtils.setField(assembler, "maxFileTokens", 5); // 20 chars max
            Map<String, String> contents = Map.of("big.java", "y".repeat(200));

            // Should not throw StringIndexOutOfBoundsException
            Map<String, String> result = assembler.truncateFileContents(contents, 100000);

            // Too small for marker, just hard-truncated to 20 chars
            assertThat(result.get("big.java")).hasSize(20);
            assertThat(result.get("big.java")).isEqualTo("y".repeat(20));
        }
    }

    @Nested
    @DisplayName("Aggregate Token Budget")
    class AggregateTokenBudget {

        @Test
        @DisplayName("Should enforce aggregate budget: file contents limited by remaining tokens after diff")
        void shouldEnforceAggregateBudget() {
            // Set up: 50 token total budget, diff uses 25 tokens → 25 tokens left for files
            ReflectionTestUtils.setField(assembler, "maxContextTokens", 50);
            ReflectionTestUtils.setField(assembler, "maxFileTokens", 100); // per-file limit is higher

            ReviewTask task = buildTask();
            String diff = "d".repeat(100); // 100 chars = 25 tokens
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().newPath("big.java")
                            .changeType(ChangeType.ADD).language(Language.JAVA)
                            .isBinary(false).linesAdded(50).linesDeleted(0).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(diff).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(diff)).thenReturn(metadata);
            // File content is 200 chars = 50 tokens, but only 25 tokens budget remaining
            doReturn("f".repeat(200)).when(gitClient)
                    .getFileContent(REPO_URL, COMMIT_HASH, "big.java");

            CodeContext result = assembler.assembleContext(task);

            // diff fits within budget (100 chars <= 200 chars max)
            assertThat(result.getRawDiff()).isEqualTo(diff);
            // file should be truncated to fit remaining budget (25 tokens = 100 chars)
            assertThat(result.getFileContents().get("big.java").length()).isLessThanOrEqualTo(100);
            assertThat(result.getFileContents().get("big.java")).contains("[TRUNCATED:");
        }

        @Test
        @DisplayName("Should skip files when aggregate budget is exhausted")
        void shouldSkipFilesWhenBudgetExhausted() {
            // Budget: 30 tokens = 120 chars. Diff: 100 chars = 25 tokens → 5 tokens (20 chars) for files
            ReflectionTestUtils.setField(assembler, "maxContextTokens", 30);
            ReflectionTestUtils.setField(assembler, "maxFileTokens", 100);

            ReviewTask task = buildTask();
            String diff = "d".repeat(100);
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().oldPath("a.java").newPath("a.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(5).linesDeleted(2).build(),
                    FileDiffInfo.builder().newPath("b.java")
                            .changeType(ChangeType.ADD).language(Language.JAVA)
                            .isBinary(false).linesAdded(3).linesDeleted(0).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(diff).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(diff)).thenReturn(metadata);
            doReturn("content-a".repeat(10)).when(gitClient) // 90 chars
                    .getFileContent(REPO_URL, COMMIT_HASH, "a.java");
            doReturn("content-b".repeat(10)).when(gitClient) // 90 chars
                    .getFileContent(REPO_URL, COMMIT_HASH, "b.java");

            CodeContext result = assembler.assembleContext(task);

            // Total file content should fit within remaining budget
            int totalFileChars = result.getFileContents().values().stream()
                    .mapToInt(String::length).sum();
            int diffTokens = result.getRawDiff().length() / 4;
            int fileTokens = totalFileChars / 4;
            assertThat(diffTokens + fileTokens).isLessThanOrEqualTo(30);
        }
    }

    @Nested
    @DisplayName("MaxFiles Limit")
    class MaxFilesLimit {

        @Test
        @DisplayName("Should enforce maxFiles limit")
        void shouldEnforceMaxFilesLimit() {
            ReflectionTestUtils.setField(assembler, "maxFiles", 2);
            ReviewTask task = buildTask();

            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().newPath("a.java")
                            .changeType(ChangeType.ADD).language(Language.JAVA)
                            .isBinary(false).linesAdded(10).linesDeleted(0).build(),
                    FileDiffInfo.builder().oldPath("b.java").newPath("b.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(5).linesDeleted(2).build(),
                    FileDiffInfo.builder().newPath("c.java")
                            .changeType(ChangeType.ADD).language(Language.JAVA)
                            .isBinary(false).linesAdded(3).linesDeleted(0).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(SAMPLE_DIFF).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(SAMPLE_DIFF)).thenReturn(metadata);
            doReturn("content").when(gitClient).getFileContent(eq(REPO_URL), eq(COMMIT_HASH), anyString());

            CodeContext result = assembler.assembleContext(task);

            // Only 2 file contents fetched despite 3 files in metadata
            assertThat(result.getFileContents()).hasSize(2);
            // But all 3 files appear in the files metadata list
            assertThat(result.getFiles()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Degradation Scenarios")
    class DegradationScenarios {

        @Test
        @DisplayName("Should skip files that fail to fetch")
        void shouldSkipFailedFiles() {
            ReviewTask task = buildTask();
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().oldPath("ok.java").newPath("ok.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(3).linesDeleted(1).build(),
                    FileDiffInfo.builder().oldPath("fail.java").newPath("fail.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(2).linesDeleted(1).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(SAMPLE_DIFF).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(SAMPLE_DIFF)).thenReturn(metadata);
            doReturn("ok content").when(gitClient)
                    .getFileContent(REPO_URL, COMMIT_HASH, "ok.java");
            doThrow(new GitApiException(404, "Not found")).when(gitClient)
                    .getFileContent(REPO_URL, COMMIT_HASH, "fail.java");

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getFileContents()).hasSize(1);
            assertThat(result.getFileContents()).containsKey("ok.java");
            assertThat(result.getFileContents()).doesNotContainKey("fail.java");
            // Files metadata still has both
            assertThat(result.getFiles()).hasSize(2);
        }

        @Test
        @DisplayName("Should return context with rawDiff only when all file fetches fail")
        void shouldDegradeToRawDiffOnly() {
            ReviewTask task = buildTask();
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().oldPath("a.java").newPath("a.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(1).linesDeleted(1).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(SAMPLE_DIFF).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(SAMPLE_DIFF)).thenReturn(metadata);
            doThrow(new GitApiException(500, "Server error")).when(gitClient)
                    .getFileContent(anyString(), anyString(), anyString());

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getRawDiff()).isEqualTo(SAMPLE_DIFF);
            assertThat(result.getFileContents()).isEmpty();
            assertThat(result.getFiles()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle empty diff gracefully")
        void shouldHandleEmptyDiff() {
            ReviewTask task = buildTask();
            DiffMetadata emptyMeta = DiffMetadata.builder()
                    .files(List.of())
                    .statistics(DiffStatistics.builder()
                            .totalFilesChanged(0).totalLinesAdded(0).totalLinesDeleted(0)
                            .build())
                    .build();

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn("").when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata("")).thenReturn(emptyMeta);

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getRawDiff()).isEmpty();
            assertThat(result.getFiles()).isEmpty();
            assertThat(result.getFileContents()).isEmpty();
            assertThat(result.getStatistics().getTotalFilesChanged()).isZero();
        }

        @Test
        @DisplayName("Should handle diff fetch failure by returning empty diff")
        void shouldHandleDiffFetchFailure() {
            ReviewTask task = buildTask();
            DiffMetadata emptyMeta = DiffMetadata.builder()
                    .files(List.of())
                    .statistics(DiffStatistics.builder()
                            .totalFilesChanged(0).totalLinesAdded(0).totalLinesDeleted(0)
                            .build())
                    .build();

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doThrow(new GitApiException(500, "Server error")).when(gitClient)
                    .getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata("")).thenReturn(emptyMeta);

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getRawDiff()).isEmpty();
            assertThat(result.getFiles()).isEmpty();
        }

        @Test
        @DisplayName("Should handle client resolution failure gracefully")
        void shouldHandleClientResolutionFailure() {
            ReviewTask task = buildTask();
            DiffMetadata emptyMeta = DiffMetadata.builder()
                    .files(List.of())
                    .statistics(DiffStatistics.builder()
                            .totalFilesChanged(0).totalLinesAdded(0).totalLinesDeleted(0)
                            .build())
                    .build();

            doThrow(new RuntimeException("Unknown platform")).when(clientFactory).getClient(REPO_URL);
            when(diffExtractor.extractMetadata("")).thenReturn(emptyMeta);

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getRawDiff()).isEmpty();
            assertThat(result.getFileContents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("File Filtering")
    class FileFiltering {

        @Test
        @DisplayName("Should skip binary files in content fetching")
        void shouldSkipBinaryFiles() {
            ReviewTask task = buildTask();
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().oldPath("image.png").newPath("image.png")
                            .changeType(ChangeType.MODIFY).language(Language.UNKNOWN)
                            .isBinary(true).linesAdded(0).linesDeleted(0).build(),
                    FileDiffInfo.builder().oldPath("code.java").newPath("code.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(5).linesDeleted(2).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(SAMPLE_DIFF).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(SAMPLE_DIFF)).thenReturn(metadata);
            doReturn("java code").when(gitClient)
                    .getFileContent(REPO_URL, COMMIT_HASH, "code.java");

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getFileContents()).hasSize(1);
            assertThat(result.getFileContents()).containsKey("code.java");
            // Binary file NOT fetched
            verify(gitClient, never()).getFileContent(REPO_URL, COMMIT_HASH, "image.png");
        }

        @Test
        @DisplayName("Should skip deleted files in content fetching")
        void shouldSkipDeletedFiles() {
            ReviewTask task = buildTask();
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().oldPath("deleted.java")
                            .changeType(ChangeType.DELETE).language(Language.JAVA)
                            .isBinary(false).linesAdded(0).linesDeleted(10).build(),
                    FileDiffInfo.builder().oldPath("kept.java").newPath("kept.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(3).linesDeleted(1).build()
            );
            DiffMetadata metadata = buildMetadata(files);

            doReturn(gitClient).when(clientFactory).getClient(REPO_URL);
            doReturn(SAMPLE_DIFF).when(gitClient).getDiff(REPO_URL, COMMIT_HASH);
            when(diffExtractor.extractMetadata(SAMPLE_DIFF)).thenReturn(metadata);
            doReturn("kept content").when(gitClient)
                    .getFileContent(REPO_URL, COMMIT_HASH, "kept.java");

            CodeContext result = assembler.assembleContext(task);

            assertThat(result.getFileContents()).hasSize(1);
            assertThat(result.getFileContents()).containsKey("kept.java");
            verify(gitClient, never()).getFileContent(REPO_URL, COMMIT_HASH, "deleted.java");
        }
    }

    @Nested
    @DisplayName("File Sorting by Line Changes (H2 fix)")
    class FileSortingByLineChanges {

        @Test
        @DisplayName("Should sort files by total line changes descending")
        void shouldSortByTotalLineChanges() {
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().newPath("small.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(1).linesDeleted(0).build(),
                    FileDiffInfo.builder().newPath("big.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(50).linesDeleted(20).build(),
                    FileDiffInfo.builder().newPath("medium.java")
                            .changeType(ChangeType.ADD).language(Language.JAVA)
                            .isBinary(false).linesAdded(10).linesDeleted(0).build()
            );

            List<FileDiffInfo> sorted = assembler.sortByChangePriority(files);

            assertThat(sorted.get(0).getNewPath()).isEqualTo("big.java");     // 70 changes
            assertThat(sorted.get(1).getNewPath()).isEqualTo("medium.java");  // 10 changes
            assertThat(sorted.get(2).getNewPath()).isEqualTo("small.java");   // 1 change
        }

        @Test
        @DisplayName("Should handle files with zero line changes (e.g., renames)")
        void shouldHandleZeroLineChanges() {
            List<FileDiffInfo> files = List.of(
                    FileDiffInfo.builder().oldPath("renamed.java").newPath("new-name.java")
                            .changeType(ChangeType.RENAME).language(Language.JAVA)
                            .isBinary(false).linesAdded(0).linesDeleted(0).build(),
                    FileDiffInfo.builder().newPath("modified.java")
                            .changeType(ChangeType.MODIFY).language(Language.JAVA)
                            .isBinary(false).linesAdded(5).linesDeleted(3).build()
            );

            List<FileDiffInfo> sorted = assembler.sortByChangePriority(files);

            assertThat(sorted.get(0).getNewPath()).isEqualTo("modified.java"); // 8 changes
            assertThat(sorted.get(1).getNewPath()).isEqualTo("new-name.java"); // 0 changes
        }
    }

    @Nested
    @DisplayName("FileInfo List Building")
    class FileInfoListBuilding {

        @Test
        @DisplayName("Should use newPath for non-DELETE files")
        void shouldUseNewPathForNonDelete() {
            DiffMetadata metadata = buildMetadata(List.of(
                    FileDiffInfo.builder().oldPath("old.java").newPath("new.java")
                            .changeType(ChangeType.RENAME).language(Language.JAVA)
                            .isBinary(false).build()
            ));

            List<FileInfo> result = assembler.buildFileInfoList(metadata);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("new.java");
        }

        @Test
        @DisplayName("Should use oldPath for DELETE files")
        void shouldUseOldPathForDelete() {
            DiffMetadata metadata = buildMetadata(List.of(
                    FileDiffInfo.builder().oldPath("removed.java")
                            .changeType(ChangeType.DELETE).language(Language.JAVA)
                            .isBinary(false).build()
            ));

            List<FileInfo> result = assembler.buildFileInfoList(metadata);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("removed.java");
        }

        @Test
        @DisplayName("Should preserve changeType and language")
        void shouldPreserveMetadata() {
            DiffMetadata metadata = buildMetadata(List.of(
                    FileDiffInfo.builder().newPath("app.py")
                            .changeType(ChangeType.ADD).language(Language.PYTHON)
                            .isBinary(false).build()
            ));

            List<FileInfo> result = assembler.buildFileInfoList(metadata);

            assertThat(result.get(0).getChangeType()).isEqualTo(ChangeType.ADD);
            assertThat(result.get(0).getLanguage()).isEqualTo(Language.PYTHON);
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("Should throw for null task")
        void shouldThrowForNullTask() {
            assertThatThrownBy(() -> assembler.assembleContext(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("Should throw for task without repoUrl")
        void shouldThrowForMissingRepoUrl() {
            ReviewTask task = ReviewTask.builder()
                    .id(1L)
                    .commitHash(COMMIT_HASH)
                    .build();

            assertThatThrownBy(() -> assembler.assembleContext(task))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("repoUrl");
        }

        @Test
        @DisplayName("Should throw for task without commitHash")
        void shouldThrowForMissingCommitHash() {
            ReviewTask task = ReviewTask.builder()
                    .id(1L)
                    .repoUrl(REPO_URL)
                    .build();

            assertThatThrownBy(() -> assembler.assembleContext(task))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("commitHash");
        }
    }

    @Nested
    @DisplayName("Token Estimation")
    class TokenEstimation {

        @Test
        @DisplayName("Should estimate tokens as length/4")
        void shouldEstimateTokens() {
            assertThat(assembler.estimateTokens("12345678")).isEqualTo(2);
            assertThat(assembler.estimateTokens("1234")).isEqualTo(1);
            assertThat(assembler.estimateTokens("")).isZero();
        }

        @Test
        @DisplayName("Should return 0 for null")
        void shouldReturnZeroForNull() {
            assertThat(assembler.estimateTokens(null)).isZero();
        }
    }
}
