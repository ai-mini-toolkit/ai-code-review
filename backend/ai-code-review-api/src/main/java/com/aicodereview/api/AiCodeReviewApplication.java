package com.aicodereview.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Main Spring Boot application class for AI Code Review system.
 *
 * Note: DataSource auto-configuration is excluded until Story 1.3 (PostgreSQL configuration).
 */
@SpringBootApplication(
    scanBasePackages = "com.aicodereview",
    exclude = {DataSourceAutoConfiguration.class}
)
public class AiCodeReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodeReviewApplication.class, args);
    }
}
