package com.aicodereview.common.dto.aimodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for AI model connection test results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestConnectionResponse {

    private boolean success;
    private String message;
    private Long responseTimeMs;
}
