package com.aicodereview.common.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Statistics about review issues, aggregated by severity and category.
 *
 * @since 5.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStatisticsDTO {

    private int total;
    private Map<String, Integer> bySeverity;
    private Map<String, Integer> byCategory;
}
