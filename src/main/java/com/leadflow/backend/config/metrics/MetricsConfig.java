package com.leadflow.backend.config.metrics;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class MetricsConfig {

    /*
     * Global tags: Applied to all metrics for consistent identification
     * Uses centralized MetricsTags for consistency across all configurations
     */
    @Bean
    public MeterFilter commonTags() {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("application", MetricsTags.getApplication()));
        tags.add(Tag.of("environment", MetricsTags.getEnvironment()));
        tags.add(Tag.of("service", MetricsTags.getService()));
        
        return MeterFilter.commonTags(tags);
    }
}