package com.aicodereview.common.dto;

import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.Language;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata about a single file's changes in a Git diff.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDiffInfo {
    private String oldPath;
    private String newPath;
    private ChangeType changeType;
    private Language language;
    private boolean isBinary;
}
