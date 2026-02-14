package com.aicodereview.common.enums;

/**
 * Categories for code review issues found by AI analysis.
 * <p>
 * Each category maps to one of the six review dimensions:
 * Security, Performance, Maintainability, Correctness, Style, Best Practices.
 * </p>
 *
 * @since 4.1.0
 */
public enum IssueCategory {

    SECURITY("Security"),
    PERFORMANCE("Performance"),
    MAINTAINABILITY("Maintainability"),
    CORRECTNESS("Correctness"),
    STYLE("Code Style"),
    BEST_PRACTICES("Best Practices");

    private final String displayName;

    IssueCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name for this category.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
