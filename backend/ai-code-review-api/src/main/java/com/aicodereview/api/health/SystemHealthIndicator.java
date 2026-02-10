package com.aicodereview.api.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

@Component("systemHealth")
@RequiredArgsConstructor
@Slf4j
public class SystemHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;
    private final DataSource dataSource;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean allHealthy = true;

        // Check Redis
        String redisStatus = checkRedis();
        builder.withDetail("redis.status", redisStatus);
        if (!"UP".equals(redisStatus)) {
            allHealthy = false;
        }

        // Check Database
        String dbStatus = checkDatabase();
        builder.withDetail("db.status", dbStatus);
        if (!"UP".equals(dbStatus)) {
            allHealthy = false;
        }

        builder.withDetail("timestamp", Instant.now().toString());

        if (!allHealthy) {
            return builder.down().build();
        }
        return builder.build();
    }

    private String checkRedis() {
        try {
            RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                return "DOWN";
            }
            try (RedisConnection connection = connectionFactory.getConnection()) {
                String pong = connection.ping();
                return "PONG".equals(pong) ? "UP" : "DOWN";
            }
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(3)) {
                return "UP";
            }
            return "DOWN";
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }
}
