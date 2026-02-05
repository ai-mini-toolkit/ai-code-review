package com.aicodereview.common.constant;

/**
 * Application-wide constants
 */
public final class AppConstants {

    private AppConstants() {
        // Prevent instantiation
    }

    public static final String APPLICATION_NAME = "ai-code-review";
    public static final String API_VERSION = "v1";
    public static final String API_BASE_PATH = "/api/" + API_VERSION;

    // Date/Time formats
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    // Default values
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}
