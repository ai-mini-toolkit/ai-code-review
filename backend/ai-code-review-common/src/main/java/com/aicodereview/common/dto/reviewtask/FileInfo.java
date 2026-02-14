package com.aicodereview.common.dto.reviewtask;

import com.aicodereview.common.enums.ChangeType;
import com.aicodereview.common.enums.Language;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified file metadata for AI review context.
 * Derived from FileDiffInfo but only contains fields relevant to AI consumption.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    private String path;
    private ChangeType changeType;
    private Language language;
}
