package com.aicodereview.api.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class MetricsFilter implements Filter {

    private final MeterRegistry meterRegistry;

    public MetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest) ||
                !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String uri = httpRequest.getRequestURI();

        // Exclude actuator endpoints from custom metrics
        if (uri.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        Instant start = Instant.now();
        try {
            chain.doFilter(request, response);
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            String method = httpRequest.getMethod();
            String status = String.valueOf(httpResponse.getStatus());
            String endpoint = normalizeEndpoint(uri);

            Counter.builder("api_requests_total")
                    .description("Total API requests")
                    .tag("endpoint", endpoint)
                    .tag("method", method)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();

            Timer.builder("api_response_time_seconds")
                    .description("API response time")
                    .tag("endpoint", endpoint)
                    .tag("method", method)
                    .register(meterRegistry)
                    .record(duration);
        }
    }

    private String normalizeEndpoint(String uri) {
        // Normalize URIs with IDs to prevent cardinality explosion
        // e.g., /api/v1/projects/123 → /api/v1/projects/{id}
        return uri.replaceAll("/\\d+", "/{id}");
    }
}
