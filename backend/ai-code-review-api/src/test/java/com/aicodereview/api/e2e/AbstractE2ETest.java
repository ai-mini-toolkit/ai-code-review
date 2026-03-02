package com.aicodereview.api.e2e;

import com.aicodereview.api.e2e.support.MockAIProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;

/**
 * Abstract base class for all E2E integration tests.
 *
 * <p>Starts isolated PostgreSQL and Redis containers via TestContainers,
 * injecting connection details dynamically so tests never depend on
 * external running services. Uses {@code e2e} Spring profile.</p>
 *
 * <p>All E2E test classes should extend this base class.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * class MyE2ETest extends AbstractE2ETest {
 *     &#64;Test
 *     void myTest() { ... }
 * }
 * </pre>
 *
 * @since 9.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// MockAI dual registration (both required):
// 1. @Import → registers MockAIProvider as @Primary bean for direct @Autowired injection
// 2. application-e2e.yml → ai.provider.default=mock-ai for AIProviderFactory.getDefaultProvider() lookup
// Removing either causes ReviewOrchestrator to call real AI providers (empty API key → failure).
@Import(MockAIProvider.MockAIConfiguration.class)
@SuppressWarnings("resource") // Containers are started once via static initializer and cleaned up by Ryuk on JVM exit
public abstract class AbstractE2ETest {

    /** Singleton PostgreSQL container — started once for all E2E test classes, never stopped during the run. */
    static final PostgreSQLContainer<?> POSTGRES;

    /** Singleton Redis container — started once for all E2E test classes, never stopped during the run. */
    static final GenericContainer<?> REDIS;

    // Singleton container pattern: containers start once when this class is loaded.
    // This prevents container stop/restart between test classes that share or cache
    // Spring contexts with different @MockBean/@SpyBean combinations.
    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("e2e_testdb")
                .withUsername("e2e_user")
                .withPassword("e2e_pass");
        POSTGRES.start();

        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        REDIS.start();
    }

    /**
     * Injects dynamic container connection properties into the Spring context
     * before the application starts.
     */
    @DynamicPropertySource
    static void configureContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * Configures Apache HttpClient on every test to avoid HttpRetryException
     * on 401 responses (same pattern as existing integration tests).
     */
    @BeforeEach
    void configureHttpClient() {
        restTemplate.getRestTemplate()
                .setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        restTemplate.getRestTemplate()
                .setErrorHandler(new DefaultResponseErrorHandler() {
                    @Override
                    public boolean hasError(@NonNull ClientHttpResponse response) throws IOException {
                        return false;
                    }
                });
    }
}
