package com.aicodereview.repository.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA Configuration
 *
 * Configures JPA repositories and entity scanning for the repository module.
 * Enables transaction management for database operations.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.aicodereview.repository")
@EntityScan(basePackages = "com.aicodereview.repository.entity")
@EnableTransactionManagement
public class JpaConfig {
    // JPA configuration is handled through application.yml
    // This class provides component scanning configuration
}
