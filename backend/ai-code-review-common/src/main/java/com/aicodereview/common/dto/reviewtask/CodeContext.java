package com.aicodereview.common.dto.reviewtask;

import com.aicodereview.common.dto.DiffStatistics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI-friendly code review context assembled from diff metadata, file contents, and task information.
 * This is the primary input for the AI review engine (Epic 4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeContext {
    private String rawDiff;
    private List<FileInfo> files;
    private Map<String, String> fileContents;
    private DiffStatistics statistics;
    private TaskMetadata taskMeta;
}
