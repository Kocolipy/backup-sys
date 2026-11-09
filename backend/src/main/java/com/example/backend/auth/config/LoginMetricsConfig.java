package com.example.backend.auth.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The {@code login} counter, on which every Login's ending is counted. */
@Configuration
public class LoginMetricsConfig {

    @Bean
    public LoginMetrics loginMetrics(MeterRegistry registry) {
        return new LoginMetrics(registry);
    }
}
