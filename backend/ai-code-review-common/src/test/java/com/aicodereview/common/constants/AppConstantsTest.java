package com.aicodereview.common.constants;

import com.aicodereview.common.constant.AppConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AppConstants
 */
class AppConstantsTest {

    @Test
    void shouldHaveCorrectApiVersion() {
        assertThat(AppConstants.API_VERSION).isEqualTo("v1");
    }

    @Test
    void shouldHaveCorrectApiBasePath() {
        assertThat(AppConstants.API_BASE_PATH).isEqualTo("/api/" + AppConstants.API_VERSION);
        assertThat(AppConstants.API_BASE_PATH).isEqualTo("/api/v1");
    }

    @Test
    void shouldHaveCorrectDefaultPageSize() {
        assertThat(AppConstants.DEFAULT_PAGE_SIZE).isEqualTo(20);
    }

    @Test
    void shouldHaveCorrectMaxPageSize() {
        assertThat(AppConstants.MAX_PAGE_SIZE).isEqualTo(100);
    }

    @Test
    void shouldHaveReasonablePageSizeLimits() {
        assertThat(AppConstants.DEFAULT_PAGE_SIZE)
                .isLessThanOrEqualTo(AppConstants.MAX_PAGE_SIZE);
        assertThat(AppConstants.DEFAULT_PAGE_SIZE).isPositive();
        assertThat(AppConstants.MAX_PAGE_SIZE).isPositive();
    }
}
