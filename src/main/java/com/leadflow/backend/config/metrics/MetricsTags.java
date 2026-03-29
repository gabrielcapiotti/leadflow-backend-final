package com.leadflow.backend.config.metrics;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * MetricsTags - Centralized metric tags definition
 * 
 * Purpose: Ensure all metrics use consistent tag keys across the application
 * to prevent Prometheus "tag keys mismatch" errors.
 * 
 * Rule: SAME METER NAME → SAME TAG KEYS
 * 
 * All metrics MUST use tags from this class to maintain:
 * - Tag key consistency
 * - Prometheus compatibility
 * - Query performance
 */
public class MetricsTags {

    private static final String APPLICATION = "leadflow-backend";
    private static final String ENVIRONMENT = "production";
    private static final String SERVICE = "leadflow-api";

    /**
     * Base tags: applied to ALL metrics globally
     * These define the minimum set of tags for service identification.
     *
     * @return Tags with: application, environment, service
     */
    public static Tags globalTags() {
        return Tags.of(
                Tag.of("application", APPLICATION),
                Tag.of("environment", ENVIRONMENT),
                Tag.of("service", SERVICE)
        );
    }

    /**
     * Base tags as varargs (for MeterFilter.commonTags)
     * Format: [key1, value1, key2, value2, ...]
     *
     * @return String array with alternating keys and values
     */
    public static String[] globalTagsArray() {
        return new String[]{
                "application", APPLICATION,
                "environment", ENVIRONMENT,
                "service", SERVICE
        };
    }

    /**
     * Webhook-specific tags (extends global tags)
     * 
     * @param eventType webhook event type (e.g., "trigger.created", "lead.qualified")
     * @return Tags with: application, environment, service, eventType
     */
    public static Tags webhookTags(String eventType) {
        return globalTags().and(Tag.of("event_type", eventType));
    }

    /**
     * CircuitBreaker-specific tags (extends global tags)
     * 
     * @param name circuit breaker name
     * @param state current state (CLOSED, OPEN, HALF_OPEN)
     * @return Tags with: application, environment, service, name, state
     */
    public static Tags circuitBreakerTags(String name, String state) {
        return globalTags()
                .and(Tag.of("name", name))
                .and(Tag.of("state", state));
    }

    /**
     * Lead-specific tags (extends global tags)
     * 
     * @param source lead source
     * @return Tags with: application, environment, service, source
     */
    public static Tags leadTags(String source) {
        return globalTags().and(Tag.of("source", source));
    }

    /**
     * AI-execution specific tags (extends global tags)
     * 
     * @param vendor AI vendor (e.g., "openai", "claude")
     * @return Tags with: application, environment, service, vendor
     */
    public static Tags aiTags(String vendor) {
        return globalTags().and(Tag.of("vendor", vendor));
    }

    /**
     * Helper: Convert Tags to varargs format
     * Useful for meter registration methods that accept String...
     * 
     * @param tags the Tags object
     * @return String array with alternating keys and values
     */
    public static String[] toArray(Tags tags) {
        List<String> result = new ArrayList<>();
        for (Tag tag : tags) {
            result.add(tag.getKey());
            result.add(tag.getValue());
        }
        return result.toArray(new String[0]);
    }

    /**
     * Get application name
     */
    public static String getApplication() {
        return APPLICATION;
    }

    /**
     * Get environment
     */
    public static String getEnvironment() {
        return ENVIRONMENT;
    }

    /**
     * Get service name
     */
    public static String getService() {
        return SERVICE;
    }
}
