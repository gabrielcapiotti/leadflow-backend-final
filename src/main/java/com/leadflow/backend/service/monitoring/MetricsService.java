package com.leadflow.backend.service.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final String applicationName;
    private final String environment;
    private final String service;

    private final Map<String, Counter> leadCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> hotLeadCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> aiExecutionCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> aiFailureCounters = new ConcurrentHashMap<>();

    public MetricsService(
            MeterRegistry registry,
            @Value("${spring.application.name:leadflow-backend}") String applicationName,
            @Value("${app.environment:development}") String environment,
            @Value("${app.service:api}") String service
    ) {
        this.registry = registry;
        this.applicationName = applicationName;
        this.environment = environment;
        this.service = service;
    }

    private Tags getBaseTags(String vendor) {
        return Tags.of(
                "application", applicationName,
                "environment", environment,
                "service", service,
                "vendor", vendor
        );
    }

    /*
     * Vendor global (para agregação total)
     */
    private static final String GLOBAL_VENDOR = "global";

    /*
     * ------------------------------
     * LEADS
     * ------------------------------
     */

    public void incrementLeadCreated() {
        incrementLeadCreated(GLOBAL_VENDOR);
    }

    public void incrementLeadCreated(String vendorId) {
        leadCounters
                .computeIfAbsent(vendorId, id ->
                        registry.counter(
                                "lead.created.total",
                                getBaseTags(id)
                        ))
                .increment();
    }

    public void incrementHotLead() {
        incrementHotLead(GLOBAL_VENDOR);
    }

    public void incrementHotLead(String vendorId) {
        hotLeadCounters
                .computeIfAbsent(vendorId, id ->
                        registry.counter(
                                "lead.hot.total",
                                getBaseTags(id)
                        ))
                .increment();
    }

    /*
     * ------------------------------
     * AI
     * ------------------------------
     */

    public void incrementAiExecutions() {
        incrementAiExecutions(GLOBAL_VENDOR);
    }

    public void incrementAiExecutions(String vendorId) {
        aiExecutionCounters
                .computeIfAbsent(vendorId, id ->
                        registry.counter(
                                "ai.executions.total",
                                getBaseTags(id)
                        ))
                .increment();
    }

    public void incrementAiFailures() {
        incrementAiFailures(GLOBAL_VENDOR);
    }

    public void incrementAiFailures(String vendorId) {
        aiFailureCounters
                .computeIfAbsent(vendorId, id ->
                        registry.counter(
                                "ai.failures.total",
                                getBaseTags(id)
                        ))
                .increment();
    }
}