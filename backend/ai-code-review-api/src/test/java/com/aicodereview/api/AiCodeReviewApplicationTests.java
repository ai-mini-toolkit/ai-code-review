package com.aicodereview.api;

import com.aicodereview.api.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify Spring Boot application context loads successfully
 * and all critical beans are configured correctly
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiCodeReviewApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // Verify that the Spring application context loads successfully
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isGreaterThan(0);
    }

    @Test
    void globalExceptionHandlerBeanExists() {
        // Verify that GlobalExceptionHandler is properly registered
        assertThat(applicationContext.containsBean("globalExceptionHandler")).isTrue();
        GlobalExceptionHandler handler = applicationContext.getBean(GlobalExceptionHandler.class);
        assertThat(handler).isNotNull();
    }

    @Test
    void applicationHasCorrectName() {
        // Verify application name is configured correctly
        String appName = applicationContext.getEnvironment().getProperty("spring.application.name");
        assertThat(appName).isEqualTo("ai-code-review");
    }
}
