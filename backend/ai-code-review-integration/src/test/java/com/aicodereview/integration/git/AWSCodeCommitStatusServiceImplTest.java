package com.aicodereview.integration.git;

import com.aicodereview.common.dto.checkrun.CommitStatusResponseDTO;
import com.aicodereview.common.dto.result.ReviewStatisticsDTO;
import com.aicodereview.common.dto.threshold.ThresholdValidationResultDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AWSCodeCommitStatusServiceImpl (stub).
 *
 * @since 6.4.0
 */
@DisplayName("AWSCodeCommitStatusServiceImpl Unit Tests")
class AWSCodeCommitStatusServiceImplTest {

    private final AWSCodeCommitStatusServiceImpl service = new AWSCodeCommitStatusServiceImpl();

    @Test
    @DisplayName("Should return null (stub not implemented)")
    void shouldReturnNull() {
        ReviewStatisticsDTO stats = ReviewStatisticsDTO.builder()
                .total(3)
                .bySeverity(Map.of("HIGH", 1, "MEDIUM", 2))
                .byCategory(Map.of())
                .build();
        ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                .passed(true).action(null).build();

        CommitStatusResponseDTO result = service.updateCommitStatus(
                "https://git-codecommit.us-east-1.amazonaws.com/v1/repos/test",
                "abc123", stats, threshold);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should not throw exception for any input")
    void shouldNotThrowException() {
        ThresholdValidationResultDTO threshold = ThresholdValidationResultDTO.builder()
                .passed(false).action("BLOCK_MERGE").build();
        ReviewStatisticsDTO stats = ReviewStatisticsDTO.builder()
                .total(0).bySeverity(Map.of()).byCategory(Map.of()).build();

        CommitStatusResponseDTO result = service.updateCommitStatus(
                "https://codecommit/repo", "def456", stats, threshold);

        assertThat(result).isNull();
    }
}
