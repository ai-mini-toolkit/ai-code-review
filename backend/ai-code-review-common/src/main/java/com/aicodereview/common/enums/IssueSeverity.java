package com.aicodereview.common.enums;

/**
 * Severity levels for code review issues found by AI analysis.
 * <p>
 * Each severity has a numeric score for ordering:
 * CRITICAL(5) > HIGH(4) > MEDIUM(3) > LOW(2) > INFO(1).
 * </p>
 *
 * @since 4.1.0
 */
public enum IssueSeverity {

    CRITICAL(5),
    HIGH(4),
    MEDIUM(3),
    LOW(2),
    INFO(1);

    private final int score;

    IssueSeverity(int score) {
        this.score = score;
    }

    /**
     * Gets the numeric score for severity ordering.
     *
     * @return the severity score (higher = more severe)
     */
    public int getScore() {
        return score;
    }
}
