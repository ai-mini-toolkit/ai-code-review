package com.aicodereview.common.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata about the AI review execution (model, tokens, timing, degradation events).
 *
 * @since 4.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewMetadata {

    private String providerId;
    private String model;
    private int promptTokens;
    private int completionTokens;
    private long durationMs;

    @Builder.Default
    private List<String> degradationEvents = new ArrayList<>();
}
