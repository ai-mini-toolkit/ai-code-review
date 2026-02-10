package com.aicodereview.common.dto.prompttemplate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for template preview rendering result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewResponse {

    private String renderedContent;
    private Long renderTimeMs;
}
