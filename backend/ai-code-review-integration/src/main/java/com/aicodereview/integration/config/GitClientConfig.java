package com.aicodereview.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration for the shared HttpClient used by Git platform API clients.
 */
@Configuration
public class GitClientConfig {

    @Bean
    public HttpClient gitHttpClient(
            @Value("${git.platform.connect-timeout-seconds:5}") int connectTimeoutSeconds) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }
}
