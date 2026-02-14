package com.aicodereview.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate statistics for a Git diff.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffStatistics {
    private int totalFilesChanged;
    private int totalLinesAdded;
    private int totalLinesDeleted;
}
