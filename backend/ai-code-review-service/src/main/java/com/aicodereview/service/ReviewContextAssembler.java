package com.aicodereview.service;

import com.aicodereview.common.dto.DiffMetadata;
import com.aicodereview.common.dto.FileDiffInfo;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.dto.reviewtask.FileInfo;
import com.aicodereview.common.dto.reviewtask.TaskMetadata;
import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.integration.git.GitPlatformClient;
import com.aicodereview.integration.git.GitPlatformClientFactory;
import com.aicodereview.repository.entity.ReviewTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the assembly of AI review context by coordinating
 * DiffMetadataExtractor and GitPlatformClient to produce a CodeContext.
 * <p>
 * Handles context window management with an aggregate token budget:
 * rawDiff gets priority, then file contents consume remaining capacity.
 * </p>
 */
@Service
@Slf4j
public class ReviewContextAssembler {

    private static final int CHARS_PER_TOKEN = 4;
    private static final String TRUNCATION_MARKER = "\n[TRUNCATED: content too large, truncated to fit token limit]";

    private final GitPlatformClientFactory clientFactory;
    private final DiffMetadataExtractor diffExtractor;

    @Value("${review.context.max-context-tokens:100000}")
    private int maxContextTokens;

    @Value("${review.context.max-file-tokens:10000}")
    private int maxFileTokens;

    @Value("${review.context.max-files:50}")
    private int maxFiles;

    public ReviewContextAssembler(GitPlatformClientFactory clientFactory,
                                  DiffMetadataExtractor diffExtractor) {
        this.clientFactory = clientFactory;
        this.diffExtractor = diffExtractor;
    }

    /**
     * Assembles a complete CodeContext for the given review task.
     * <p>
     * Uses an aggregate token budget (maxContextTokens): rawDiff gets priority,
     * then file contents consume remaining capacity within the budget.
     * </p>
     *
     * @param task the review task containing repo, commit, and PR/MR information
     * @return assembled CodeContext ready for AI consumption
     */
    public CodeContext assembleContext(ReviewTask task) {
        if (task == null) {
            throw new IllegalArgumentException("ReviewTask must not be null");
        }
        if (task.getRepoUrl() == null || task.getCommitHash() == null) {
            throw new IllegalArgumentException("ReviewTask must have repoUrl and commitHash");
        }

        log.info("Assembling review context for task {} (repo: {}, commit: {})",
                task.getId(), task.getRepoUrl(), task.getCommitHash());

        // Resolve Git client once for reuse (M3 fix)
        GitPlatformClient client = resolveClient(task);

        // Step 1: Fetch raw diff
        String rawDiff = fetchRawDiff(client, task);

        // Step 2: Extract metadata
        DiffMetadata metadata = diffExtractor.extractMetadata(rawDiff);

        // Step 3: Fetch file contents with error handling
        Map<String, String> fileContents = fetchFileContents(client, task, metadata);

        // Step 4: Apply aggregate token budget (H1 fix)
        // rawDiff gets priority, then file contents use remaining budget
        rawDiff = truncateRawDiff(rawDiff);
        int remainingTokens = maxContextTokens - estimateTokens(rawDiff);
        fileContents = truncateFileContents(fileContents, remainingTokens);

        // Step 5: Build CodeContext
        CodeContext context = CodeContext.builder()
                .rawDiff(rawDiff)
                .files(buildFileInfoList(metadata))
                .fileContents(fileContents)
                .statistics(metadata.getStatistics())
                .taskMeta(buildTaskMetadata(task))
                .build();

        log.info("Context assembled: {} files metadata, {} file contents fetched, diff {} tokens, total {} tokens",
                context.getFiles().size(),
                context.getFileContents().size(),
                estimateTokens(rawDiff),
                estimateTokens(rawDiff) + fileContents.values().stream().mapToInt(this::estimateTokens).sum());

        return context;
    }

    private GitPlatformClient resolveClient(ReviewTask task) {
        try {
            return clientFactory.getClient(task.getRepoUrl());
        } catch (Exception e) {
            log.error("Failed to resolve Git client for task {}: {}", task.getId(), e.getMessage());
            return null;
        }
    }

    String fetchRawDiff(GitPlatformClient client, ReviewTask task) {
        if (client == null) {
            return "";
        }
        try {
            String diff = client.getDiff(task.getRepoUrl(), task.getCommitHash());
            return diff != null ? diff : "";
        } catch (Exception e) {
            log.error("Failed to fetch raw diff for task {}: {}", task.getId(), e.getMessage());
            return "";
        }
    }

    Map<String, String> fetchFileContents(GitPlatformClient client, ReviewTask task, DiffMetadata metadata) {
        Map<String, String> contents = new LinkedHashMap<>();

        if (client == null) {
            return contents;
        }
        if (metadata.getFiles() == null || metadata.getFiles().isEmpty()) {
            return contents;
        }

        List<FileDiffInfo> sortedFiles = sortByChangePriority(metadata.getFiles());
        int filesProcessed = 0;

        for (FileDiffInfo file : sortedFiles) {
            if (filesProcessed >= maxFiles) {
                log.info("Reached maxFiles limit ({}), skipping remaining files", maxFiles);
                break;
            }
            if (file.isBinary()) {
                continue;
            }
            if (file.getChangeType() == ChangeType.DELETE) {
                continue;
            }

            String path = file.getNewPath() != null ? file.getNewPath() : file.getOldPath();
            if (path == null) {
                continue;
            }

            try {
                String content = client.getFileContent(
                        task.getRepoUrl(), task.getCommitHash(), path);
                if (content != null) {
                    contents.put(path, content);
                    filesProcessed++;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch file content for '{}': {}", path, e.getMessage());
            }
        }

        return contents;
    }

    /**
     * Sorts files by total line changes (linesAdded + linesDeleted) descending.
     * Files with more changes are prioritized for context assembly (AC5).
     */
    List<FileDiffInfo> sortByChangePriority(List<FileDiffInfo> files) {
        return files.stream()
                .sorted(Comparator.comparingInt(
                        (FileDiffInfo f) -> f.getLinesAdded() + f.getLinesDeleted()).reversed())
                .collect(Collectors.toList());
    }

    String truncateRawDiff(String rawDiff) {
        if (rawDiff == null || rawDiff.isEmpty()) {
            return rawDiff;
        }
        int maxChars = maxContextTokens * CHARS_PER_TOKEN;
        if (rawDiff.length() <= maxChars) {
            return rawDiff;
        }
        log.warn("Raw diff exceeds token limit ({} chars > {} max chars), truncating",
                rawDiff.length(), maxChars);
        if (maxChars > TRUNCATION_MARKER.length()) {
            return rawDiff.substring(0, maxChars - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
        }
        return rawDiff.substring(0, maxChars);
    }

    /**
     * Truncates file contents to fit within both per-file and aggregate token budgets.
     *
     * @param fileContents the raw file contents
     * @param remainingTokens aggregate token budget remaining after rawDiff
     * @return truncated file contents map
     */
    Map<String, String> truncateFileContents(Map<String, String> fileContents, int remainingTokens) {
        if (fileContents == null || fileContents.isEmpty()) {
            return fileContents;
        }
        int maxFileChars = maxFileTokens * CHARS_PER_TOKEN;
        int remainingChars = Math.max(0, remainingTokens * CHARS_PER_TOKEN);
        Map<String, String> truncated = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String content = entry.getValue();
            if (content == null) {
                truncated.put(entry.getKey(), null);
                continue;
            }

            // Skip file entirely if no budget remains
            if (remainingChars <= 0) {
                log.info("Aggregate token budget exhausted, skipping file '{}'", entry.getKey());
                continue;
            }

            // Apply per-file limit
            int effectiveLimit = Math.min(maxFileChars, remainingChars);
            if (content.length() > effectiveLimit) {
                log.warn("File '{}' truncated ({} chars > {} effective limit)",
                        entry.getKey(), content.length(), effectiveLimit);
                if (effectiveLimit > TRUNCATION_MARKER.length()) {
                    content = content.substring(0, effectiveLimit - TRUNCATION_MARKER.length())
                            + TRUNCATION_MARKER;
                } else {
                    content = content.substring(0, effectiveLimit);
                }
            }

            truncated.put(entry.getKey(), content);
            remainingChars -= content.length();
        }
        return truncated;
    }

    List<FileInfo> buildFileInfoList(DiffMetadata metadata) {
        if (metadata.getFiles() == null) {
            return List.of();
        }
        List<FileInfo> fileInfos = new ArrayList<>();
        for (FileDiffInfo fileDiff : metadata.getFiles()) {
            String path = fileDiff.getChangeType() == ChangeType.DELETE
                    ? fileDiff.getOldPath()
                    : fileDiff.getNewPath();
            if (path == null) {
                path = fileDiff.getOldPath();
            }
            fileInfos.add(FileInfo.builder()
                    .path(path)
                    .changeType(fileDiff.getChangeType())
                    .language(fileDiff.getLanguage())
                    .build());
        }
        return fileInfos;
    }

    TaskMetadata buildTaskMetadata(ReviewTask task) {
        return TaskMetadata.builder()
                .prTitle(task.getPrTitle())
                .prDescription(task.getPrDescription())
                .author(task.getAuthor())
                .branch(task.getBranch())
                .commitHash(task.getCommitHash())
                .taskType(task.getTaskType())
                .build();
    }

    int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        return text.length() / CHARS_PER_TOKEN;
    }
}
