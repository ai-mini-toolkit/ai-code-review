package com.aicodereview.common.dto.reviewtask;

import com.aicodereview.common.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Task metadata extracted from ReviewTask for AI review context.
 * Contains PR/MR information and commit details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMetadata {
    private String prTitle;
    private String prDescription;
    private String author;
    private String branch;
    private String commitHash;
    private TaskType taskType;
}
