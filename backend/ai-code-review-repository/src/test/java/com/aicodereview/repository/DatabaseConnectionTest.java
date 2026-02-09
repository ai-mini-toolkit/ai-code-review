package com.aicodereview.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for database connectivity.
 *
 * These tests verify:
 * - DataSource bean is properly configured
 * - JPA EntityManagerFactory is initialized
 * - Flyway migrations have been applied
 * - Database connection is established
 *
 * Note: Requires PostgreSQL to be running (via docker-compose up)
 */
@SpringBootTest
@ActiveProfiles("dev")
class DatabaseConnectionTest {

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "com.aicodereview.repository")
    static class TestConfig {
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldLoadDataSourceBean() {
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.getClass().getName()).contains("HikariDataSource");
    }

    @Test
    void shouldConnectToPostgreSQLDatabase() throws Exception {
        DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
        assertThat(metaData.getDatabaseProductName()).isEqualTo("PostgreSQL");
        assertThat(metaData.getDatabaseMajorVersion()).isGreaterThanOrEqualTo(18);
    }

    @Test
    void shouldHaveFlywayMigrationApplied() {
        String query = "SELECT COUNT(*) FROM flyway_schema_history";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void shouldHaveSystemConfigTableCreated() {
        String query = "SELECT table_name FROM information_schema.tables " +
                      "WHERE table_schema = 'public' AND table_name = 'system_config'";
        String tableName = jdbcTemplate.queryForObject(query, String.class);
        assertThat(tableName).isEqualTo("system_config");
    }

    @Test
    void shouldHaveInitialDataInSystemConfig() {
        String query = "SELECT COUNT(*) FROM system_config";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(2);

        String versionQuery = "SELECT config_value FROM system_config WHERE config_key = 'system.version'";
        String version = jdbcTemplate.queryForObject(versionQuery, String.class);
        assertThat(version).isEqualTo("1.0.0");
    }

    @Test
    void shouldExecuteSimpleQuery() {
        String query = "SELECT 1";
        Integer result = jdbcTemplate.queryForObject(query, Integer.class);
        assertThat(result).isEqualTo(1);
    }
}
