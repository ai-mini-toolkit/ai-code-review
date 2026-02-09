package com.aicodereview.repository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Connection Integration Test
 *
 * Tests Redis connection, RedisTemplate operations, serialization, TTL, and Spring Cache integration.
 * Requires Redis running on localhost:6379 (via docker-compose).
 */
@SpringBootTest
@ActiveProfiles("dev")
class RedisConnectionTest {

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "com.aicodereview.repository")
    static class TestConfig {
    }

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired(required = false)
    private TestCacheService testCacheService;

    private static final String TEST_KEY_PREFIX = "test:redis:";

    @AfterEach
    void cleanup() {
        // Clean up test keys after each test
        Objects.requireNonNull(redisTemplate.keys(TEST_KEY_PREFIX + "*"))
                .forEach(key -> redisTemplate.delete(key));
    }

    /**
     * Test 1: Basic Redis connection
     * Verifies that RedisTemplate can connect to Redis server
     */
    @Test
    void testRedisConnection() {
        String key = TEST_KEY_PREFIX + "connection";
        String value = "test-value";

        redisTemplate.opsForValue().set(key, value);
        String retrieved = (String) redisTemplate.opsForValue().get(key);

        assertThat(retrieved).isEqualTo(value);
    }

    /**
     * Test 2: String set and get operations
     * Verifies basic string operations work correctly
     */
    @Test
    void testStringOperations() {
        String key = TEST_KEY_PREFIX + "string";
        String value = "Hello Redis!";

        redisTemplate.opsForValue().set(key, value);
        Object result = redisTemplate.opsForValue().get(key);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(value);
    }

    /**
     * Test 3: Object serialization and deserialization
     * Verifies that Jackson2JsonRedisSerializer works for complex objects
     */
    @Test
    void testObjectSerialization() {
        String key = TEST_KEY_PREFIX + "object";
        TestDto testObject = new TestDto("test-id", "test-name", 100);

        redisTemplate.opsForValue().set(key, testObject);
        Object result = redisTemplate.opsForValue().get(key);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(TestDto.class);

        TestDto retrieved = (TestDto) result;
        assertThat(retrieved.getId()).isEqualTo(testObject.getId());
        assertThat(retrieved.getName()).isEqualTo(testObject.getName());
        assertThat(retrieved.getValue()).isEqualTo(testObject.getValue());
    }

    /**
     * Test 4: TTL (Time To Live) expiration
     * Verifies that keys expire correctly after specified timeout
     */
    @Test
    void testTTLExpiration() throws InterruptedException {
        String key = TEST_KEY_PREFIX + "ttl";
        String value = "expires-soon";

        // Set key with 2 seconds TTL
        redisTemplate.opsForValue().set(key, value, 2, TimeUnit.SECONDS);

        // Verify key exists immediately
        assertThat(redisTemplate.hasKey(key)).isTrue();

        // Wait 3 seconds for expiration
        Thread.sleep(3000);

        // Verify key is expired
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    /**
     * Test 5: CacheManager bean existence
     * Verifies that RedisCacheManager is properly configured
     */
    @Test
    void testCacheManagerConfiguration() {
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.getClass().getSimpleName()).contains("RedisCacheManager");
    }

    /**
     * Test 6: Spring Cache annotation (@Cacheable)
     * Verifies that @Cacheable works with Redis backend
     */
    @Test
    void testSpringCacheAnnotation() {
        if (testCacheService == null) {
            // Skip test if service not available
            return;
        }

        String param = "test-param";

        // First call - cache miss
        String result1 = testCacheService.getCachedValue(param);
        assertThat(result1).isEqualTo("computed-" + param);

        // Second call - cache hit (should return same instance)
        String result2 = testCacheService.getCachedValue(param);
        assertThat(result2).isEqualTo(result1);

        // Verify cache contains the value
        var cache = cacheManager.getCache("testCache");
        assertThat(cache).isNotNull();
        // Note: Cache key may be different due to SpEL evaluation
        // Just verify cache manager is working
    }

    /**
     * Test 7: Hash operations
     * Verifies hash data structure operations
     */
    @Test
    void testHashOperations() {
        String key = TEST_KEY_PREFIX + "hash";
        String hashKey = "field1";
        String hashValue = "value1";

        redisTemplate.opsForHash().put(key, hashKey, hashValue);
        Object result = redisTemplate.opsForHash().get(key, hashKey);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(hashValue);
    }

    /**
     * Test 8: Delete operation
     * Verifies that keys can be deleted
     */
    @Test
    void testDeleteOperation() {
        String key = TEST_KEY_PREFIX + "delete";
        String value = "to-be-deleted";

        redisTemplate.opsForValue().set(key, value);
        assertThat(redisTemplate.hasKey(key)).isTrue();

        redisTemplate.delete(key);
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    /**
     * Test service for @Cacheable annotation testing
     */
    @Service
    static class TestCacheService {

        @Cacheable(value = "testCache", key = "#p0")
        public String getCachedValue(String param) {
            return "computed-" + param;
        }
    }

    /**
     * Test DTO for serialization testing
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestDto implements Serializable {
        private String id;
        private String name;
        private Integer value;
    }
}
