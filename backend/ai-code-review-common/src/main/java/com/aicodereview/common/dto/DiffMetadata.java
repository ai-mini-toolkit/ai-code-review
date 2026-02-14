package com.aicodereview.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Extracted metadata from a raw Git diff.
 * Contains per-file change information and aggregate statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffMetadata {
    private List<FileDiffInfo> files;
    private DiffStatistics statistics;
}
