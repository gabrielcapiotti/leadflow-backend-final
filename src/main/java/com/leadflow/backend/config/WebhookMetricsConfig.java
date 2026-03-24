package com.leadflow.backend.config;

import com.leadflow.backend.service.billing.WebhookMetricsTracker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebhookMetricsConfig - Micrometer metrics configuration for webhook system observability
 * 
 * This configuration:
 * - Initializes the Micrometer MeterRegistry (auto-detected by Spring Boot)
 * - Registers custom metrics for webhook monitoring
 * - Configures Prometheus endpoint (via actuator)
 * - Sets global tags for multi-tenant distinction
 */
@Configuration
@Slf4j
public class WebhookMetricsConfig {

    /**
     * Initialize WebhookMetricsTracker bean
     * Automatically injected with Spring's MeterRegistry
     * 
     * MeterRegistry is auto-configured by:
     * - spring-boot-starter-actuator dependency
     * - Prometheus registry picked up from classpath if available
     * - Falls back to SimpleMeterRegistry for testing
     */
    @Bean
    public WebhookMetricsTracker webhookMetricsTracker(MeterRegistry meterRegistry) {
        log.info("Initializing WebhookMetricsTracker with MeterRegistry: {}", 
                meterRegistry.getClass().getSimpleName());
        
        WebhookMetricsTracker tracker = new WebhookMetricsTracker(meterRegistry);
        tracker.initialize();
        
        return tracker;
    }

    /**
     * Optional: Customize the MeterRegistry with global tags
     * These tags appear on ALL metrics automatically
     * 
     * Useful for:
     * - Service identification in Prometheus
     * - Environment segregation
     * - Application-wide filtering
     */
    @Bean
    public MeterRegistry.Config customizeMeterRegistry(MeterRegistry registry) {
        registry.config()
                .commonTags(
                        "app", "leadflow-backend",
                        "service", "webhook-processor",
                        "module", "billing"
                );
        
        log.debug("Global meter tags configured: app=leadflow-backend, service=webhook-processor");
        return registry.config();
    }

    /**
     * Optional: Enable metrics endpoint export configuration
     * Spring Boot Actuator automatically exposes:
     * - /actuator/metrics - Lists all available metrics
     * - /actuator/metrics/{metric.name} - Individual metric details  
     * - /actuator/prometheus - Prometheus scrape endpoint (if Prometheus registry present)
     * 
     * These are auto-enabled if application.yml has:
     * management:
     *   endpoints:
     *     web:
     *       exposure:
     *         include: "metrics,prometheus"
     */
}
