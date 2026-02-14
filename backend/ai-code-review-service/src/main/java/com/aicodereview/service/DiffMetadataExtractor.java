package com.aicodereview.service;

import com.aicodereview.common.dto.DiffMetadata;
import com.aicodereview.common.dto.DiffStatistics;
import com.aicodereview.common.dto.FileDiffInfo;
import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.Language;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts structured metadata from raw Git Unified Diff content.
 * <p>
 * Only extracts file-level metadata (paths, change types, line counts).
 * Does NOT parse hunk content — raw diff is passed directly to AI.
 * </p>
 */
@Service
public class DiffMetadataExtractor {

    private static final String DIFF_GIT_PREFIX = "diff --git ";
    private static final String OLD_PATH_PREFIX = "--- ";
    private static final String NEW_PATH_PREFIX = "+++ ";
    private static final String NEW_FILE_MODE = "new file mode";
    private static final String DELETED_FILE_MODE = "deleted file mode";
    private static final String RENAME_FROM = "rename from ";
    private static final String RENAME_TO = "rename to ";
    private static final String BINARY_FILES = "Binary files ";

    /**
     * Extracts metadata from a raw Git diff string.
     *
     * @param rawDiff the raw unified diff content
     * @return DiffMetadata containing per-file info and aggregate statistics
     */
    public DiffMetadata extractMetadata(String rawDiff) {
        if (rawDiff == null || rawDiff.isBlank()) {
            return emptyMetadata();
        }

        List<String> fileSections = splitIntoFileSections(rawDiff);
        List<FileDiffInfo> files = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;

        for (String section : fileSections) {
            FileDiffInfo info = parseFileSection(section);
            if (info != null) {
                files.add(info);
                if (!info.isBinary()) {
                    int[] lineCounts = countLines(section);
                    totalAdded += lineCounts[0];
                    totalDeleted += lineCounts[1];
                }
            }
        }

        DiffStatistics statistics = DiffStatistics.builder()
                .totalFilesChanged(files.size())
                .totalLinesAdded(totalAdded)
                .totalLinesDeleted(totalDeleted)
                .build();

        return DiffMetadata.builder()
                .files(files)
                .statistics(statistics)
                .build();
    }

    private List<String> splitIntoFileSections(String rawDiff) {
        List<String> sections = new ArrayList<>();
        String[] lines = rawDiff.split("\n");
        StringBuilder current = null;

        for (String line : lines) {
            if (line.startsWith(DIFF_GIT_PREFIX)) {
                if (current != null) {
                    sections.add(current.toString());
                }
                current = new StringBuilder(line);
            } else if (current != null) {
                current.append("\n").append(line);
            }
        }
        if (current != null) {
            sections.add(current.toString());
        }
        return sections;
    }

    private FileDiffInfo parseFileSection(String section) {
        String[] lines = section.split("\n");
        if (lines.length == 0) {
            return null;
        }

        String oldPath = null;
        String newPath = null;
        ChangeType changeType = ChangeType.MODIFY;
        boolean isBinary = false;
        String renameFrom = null;
        String renameTo = null;
        String[] headerPaths = extractPathsFromDiffHeader(lines[0]);
        boolean inHunk = false;

        for (String line : lines) {
            if (line.startsWith("@@") && line.contains("@@")) {
                inHunk = true;
                continue;
            }
            if (inHunk) {
                continue;
            }
            if (line.startsWith(NEW_FILE_MODE)) {
                changeType = ChangeType.ADD;
            } else if (line.startsWith(DELETED_FILE_MODE)) {
                changeType = ChangeType.DELETE;
            } else if (line.startsWith(RENAME_FROM)) {
                renameFrom = line.substring(RENAME_FROM.length()).trim();
                changeType = ChangeType.RENAME;
            } else if (line.startsWith(RENAME_TO)) {
                renameTo = line.substring(RENAME_TO.length()).trim();
                changeType = ChangeType.RENAME;
            } else if (line.startsWith(OLD_PATH_PREFIX)) {
                String path = extractPath(line, OLD_PATH_PREFIX);
                if (!"/dev/null".equals(path)) {
                    oldPath = path;
                }
            } else if (line.startsWith(NEW_PATH_PREFIX)) {
                String path = extractPath(line, NEW_PATH_PREFIX);
                if (!"/dev/null".equals(path)) {
                    newPath = path;
                }
            } else if (line.startsWith(BINARY_FILES) && line.contains("differ")) {
                isBinary = true;
            }
        }

        // For RENAME without --- / +++ lines, use rename from/to
        if (changeType == ChangeType.RENAME) {
            if (renameFrom != null && oldPath == null) {
                oldPath = renameFrom;
            }
            if (renameTo != null && newPath == null) {
                newPath = renameTo;
            }
        }

        // Fallback: extract paths from "diff --git a/X b/Y" header for binary files
        if (oldPath == null && newPath == null && headerPaths != null) {
            if (changeType != ChangeType.ADD) {
                oldPath = headerPaths[0];
            }
            if (changeType != ChangeType.DELETE) {
                newPath = headerPaths[1];
            }
        }

        // Detect language from the most relevant path
        String pathForLanguage = newPath != null ? newPath : oldPath;
        Language language = Language.fromFileName(pathForLanguage);

        return FileDiffInfo.builder()
                .oldPath(oldPath)
                .newPath(newPath)
                .changeType(changeType)
                .language(language)
                .isBinary(isBinary)
                .build();
    }

    /**
     * Extracts old and new paths from the "diff --git a/X b/Y" header line.
     * Used as fallback when --- / +++ lines are absent (e.g., binary files).
     *
     * @return String[2] = {oldPath, newPath}, or null if header cannot be parsed
     */
    private String[] extractPathsFromDiffHeader(String headerLine) {
        if (headerLine == null || !headerLine.startsWith(DIFF_GIT_PREFIX)) {
            return null;
        }
        String rest = headerLine.substring(DIFF_GIT_PREFIX.length());
        // Format: "a/path b/path" — find the " b/" separator
        int separatorIndex = rest.indexOf(" b/");
        if (separatorIndex < 0) {
            return null;
        }
        String oldPart = rest.substring(0, separatorIndex);
        String newPart = rest.substring(separatorIndex + 1);
        // Strip "a/" and "b/" prefixes
        if (oldPart.startsWith("a/")) {
            oldPart = oldPart.substring(2);
        }
        if (newPart.startsWith("b/")) {
            newPart = newPart.substring(2);
        }
        return new String[]{oldPart, newPart};
    }

    private String extractPath(String line, String prefix) {
        String path = line.substring(prefix.length()).trim();
        // Remove "a/" or "b/" prefix from Git diff paths
        if (path.startsWith("a/") || path.startsWith("b/")) {
            path = path.substring(2);
        }
        return path;
    }

    private int[] countLines(String section) {
        int added = 0;
        int deleted = 0;
        String[] lines = section.split("\n");
        boolean inHunk = false;

        for (String line : lines) {
            if (line.startsWith("@@") && line.contains("@@")) {
                inHunk = true;
                continue;
            }
            if (inHunk) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    added++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    deleted++;
                }
            }
        }
        return new int[]{added, deleted};
    }

    private DiffMetadata emptyMetadata() {
        return DiffMetadata.builder()
                .files(List.of())
                .statistics(DiffStatistics.builder()
                        .totalFilesChanged(0)
                        .totalLinesAdded(0)
                        .totalLinesDeleted(0)
                        .build())
                .build();
    }
}
