package com.aicodereview.api.config;

import com.aicodereview.api.filter.MetricsFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class MetricsConfig {

    @Bean
    public FilterRegistrationBean<MetricsFilter> metricsFilterRegistration(MeterRegistry meterRegistry) {
        FilterRegistrationBean<MetricsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MetricsFilter(meterRegistry));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setName("metricsFilter");
        return registration;
    }
}
