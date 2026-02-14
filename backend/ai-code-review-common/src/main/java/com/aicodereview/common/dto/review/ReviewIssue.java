package com.aicodereview.common.dto.review;

import com.aicodereview.common.enums.IssueCategory;
import com.aicodereview.common.enums.IssueSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single code review issue found by AI analysis.
 *
 * @since 4.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssue {

    private IssueSeverity severity;
    private IssueCategory category;
    private String filePath;
    private Integer line;
    private String message;
    private String suggestion;
}
