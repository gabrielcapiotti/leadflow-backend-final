package com.leadflow.backend.service.billing;

import com.leadflow.backend.dto.billing.BillingDashboardDTO;
import com.leadflow.backend.dto.billing.SubscriptionDetailsDTO;
import com.leadflow.backend.dto.billing.WebhookDashboardDTO;
import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.entities.UsageLimit;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.repository.UsageLimitRepository;
import com.leadflow.backend.repository.PlanRepository;
import com.leadflow.backend.service.billing.CircuitBreakerConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingDashboardService {

    private final SubscriptionRepository subscriptionRepository;
    private final StripeEventLogRepository eventLogRepository;
    private final StripeWebhookProcessingService processingService;
    private final UsageLimitRepository usageLimitRepository;
    private final PlanRepository planRepository;

    // =====================================================
    // CORE: ENSURE SUBSCRIPTION EXISTS (CORRIGIDO)
    // =====================================================

    private Subscription ensureSubscription(UUID tenantId) {

        if (tenantId == null) {
            throw new IllegalArgumentException("TenantId cannot be null");
        }

        return subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> {

                    log.warn("No subscription found for tenant {} → creating default", tenantId);

                    Plan defaultPlan = getDefaultPlan();

                    Subscription sub = Subscription.createTrial(tenantId, defaultPlan);

                    return subscriptionRepository.save(sub);
                });
    }

    // =====================================================
    // DEFAULT PLAN (OBRIGATÓRIO)
    // =====================================================

    private Plan getDefaultPlan() {
        return planRepository.findByNameIgnoreCase("FREE")
                .orElseThrow(() ->
                        new RuntimeException("Default plan 'FREE' not found in database"));
    }

    // =====================================================
    // DASHBOARD
    // =====================================================

    public BillingDashboardDTO getBillingDashboard(UUID tenantId) {

        Subscription subscription = ensureSubscription(tenantId);

        SubscriptionDetailsDTO subscriptionDetails =
                SubscriptionDetailsDTO.fromEntity(subscription);

        var eventStats = processingService.getEventStatistics();
        double successRate = calculateSuccessRate(eventStats);

        BillingDashboardDTO.EventStatisticsDTO eventStatistics =
                BillingDashboardDTO.EventStatisticsDTO.builder()
                        .totalProcessed(eventStats.getTotalProcessed())
                        .totalFailed(eventStats.getTotalFailed())
                        .totalPending(eventStats.getTotalPending())
                        .totalRetryPending(eventStats.getTotalRetryPending())
                        .successRate(successRate)
                        .build();

        boolean isActive =
                subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE;

        String currentStatus = getStatusDisplay(subscription.getStatus());
        String nextAction = getNextAction(subscription);

        return BillingDashboardDTO.builder()
                .subscription(subscriptionDetails)
                .eventStatistics(eventStatistics)
                .hasActiveSubscription(isActive)
                .currentStatus(currentStatus)
                .nextAction(nextAction)
                .build();
    }

    // =====================================================
    // SUBSCRIPTION
    // =====================================================

    public SubscriptionDetailsDTO getSubscriptionDetails(UUID tenantId) {
        Subscription subscription = ensureSubscription(tenantId);
        return SubscriptionDetailsDTO.fromEntity(subscription);
    }

    // =====================================================
    // EVENTS
    // =====================================================

    public java.util.List<StripeEventLog> getEventHistory(UUID tenantId, int limit) {
        return eventLogRepository.findByStatuses(
                java.util.Arrays.asList(
                        StripeEventLog.EventProcessingStatus.SUCCESS,
                        StripeEventLog.EventProcessingStatus.FAILED,
                        StripeEventLog.EventProcessingStatus.RETRY_PENDING
                )
        ).stream()
                .limit(limit)
                .toList();
    }

    public boolean hasPendingRetries() {
        long pendingCount = eventLogRepository
                .countByStatus(StripeEventLog.EventProcessingStatus.RETRY_PENDING);
        return pendingCount > 0;
    }

    // =====================================================
    // USAGE
    // =====================================================

    public BillingDashboardDTO.UsageStatisticsDTO getUsageStatistics(UUID tenantId) {
        try {

            UsageLimit usageLimit = usageLimitRepository.findByTenantId(tenantId)
                    .orElse(null);

            if (usageLimit == null || usageLimit.getPlan() == null) {
                log.warn("No usage limit found for tenant: {}", tenantId);
                return createDefaultUsageStats();
            }

            Plan plan = usageLimit.getPlan();
            long leadsUsed = usageLimit.getLeadsUsed() != null
                    ? usageLimit.getLeadsUsed().longValue()
                    : 0L;

            long leadsLimit = plan.getMaxLeads() != null
                    ? plan.getMaxLeads().longValue()
                    : 0L;

            double usagePercentage =
                    (leadsLimit > 0) ? (leadsUsed * 100.0) / leadsLimit : 0.0;

            String usageStatus = getUsageStatus(usagePercentage);

            return BillingDashboardDTO.UsageStatisticsDTO.builder()
                    .leadsCreated(leadsUsed)
                    .leadsLimit(leadsLimit)
                    .usagePercentage(usagePercentage)
                    .usageStatus(usageStatus)
                    .build();

        } catch (Exception e) {
            log.error("Error calculating usage statistics for tenant: {}", tenantId, e);
            return createDefaultUsageStats();
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private String getUsageStatus(double usagePercentage) {
        if (usagePercentage >= 100.0) return "EXCEEDED";
        if (usagePercentage >= 90.0) return "CRITICAL";
        if (usagePercentage >= 75.0) return "WARNING";
        return "OK";
    }

    private BillingDashboardDTO.UsageStatisticsDTO createDefaultUsageStats() {
        return BillingDashboardDTO.UsageStatisticsDTO.builder()
                .leadsCreated(0L)
                .leadsLimit(0L)
                .usagePercentage(0.0)
                .usageStatus("UNKNOWN")
                .build();
    }

    private double calculateSuccessRate(StripeWebhookProcessingService.EventStatistics stats) {
        long total = stats.getTotalProcessed() + stats.getTotalFailed();
        if (total == 0) return 100.0;
        return (stats.getTotalProcessed() * 100.0) / total;
    }

    /**
     * Overload for calculating success rate from raw counts
     */
    private double calculateSuccessRate(long successful, long total) {
        if (total == 0) return 100.0;
        return (successful * 100.0) / total;
    }

    private String getStatusDisplay(Subscription.SubscriptionStatus status) {
        return switch (status) {
            case ACTIVE -> "✅ Ativa";
            case PAST_DUE -> "⚠️ Vencida";
            case CANCELLED -> "❌ Cancelada";
            case INCOMPLETE -> "⏳ Incompleta";
        };
    }

    private String getNextAction(Subscription subscription) {

        if (subscription.getStatus() == Subscription.SubscriptionStatus.CANCELLED) {
            return "Renovar assinatura para reativar acesso";
        }

        if (subscription.getStatus() == Subscription.SubscriptionStatus.PAST_DUE) {
            return "Atualizar método de pagamento";
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = subscription.getExpiresAt();

        if (expiresAt == null) {
            return "Sem data de expiração definida";
        }

        long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(now, expiresAt);

        if (daysUntil <= 0) return "Assinatura expirou - Renovar agora";
        if (daysUntil <= 7) return "Assinatura expira em " + daysUntil + " dias";

        return "Assinatura ativa até " + expiresAt.toLocalDate();
    }

    // =====================================================
    // WEBHOOK DASHBOARD (ETAPA 2)
    // =====================================================

    /**
     * Get comprehensive webhook dashboard metrics (system-wide, all tenants)
     */
    public WebhookDashboardDTO getWebhookDashboard() {
        log.info("Fetching webhook dashboard metrics (all tenants)");

        try {
            // Get overall metrics
            long totalReceived = eventLogRepository.count();
            long totalSuccessful = eventLogRepository
                    .countByStatus(StripeEventLog.EventProcessingStatus.SUCCESS);
            long totalFailed = eventLogRepository
                    .countByStatus(StripeEventLog.EventProcessingStatus.FAILED);
            long totalPendingRetry = eventLogRepository
                    .countByStatus(StripeEventLog.EventProcessingStatus.RETRY_PENDING);
            long totalPending = eventLogRepository
                    .countByStatus(StripeEventLog.EventProcessingStatus.PENDING);

            double successRate = calculateSuccessRate(totalSuccessful, totalReceived);
            double avgProcessingMs = calculateAverageProcessingTime();

            // Build metrics
            WebhookDashboardDTO.WebhookMetricsDTO metrics = WebhookDashboardDTO.WebhookMetricsDTO
                    .builder()
                    .totalWebhooksReceived(totalReceived)
                    .totalSuccessful(totalSuccessful)
                    .totalFailed(totalFailed)
                    .totalPendingRetry(totalPendingRetry)
                    .successRate(successRate)
                    .averageProcessingTimeMs(avgProcessingMs)
                    .build();

            // Build health status
            WebhookDashboardDTO.WebhookHealthDTO health = buildWebhookHealth(
                    totalPendingRetry + totalPending,
                    totalSuccessful,
                    totalFailed
            );

            // Get recent events
            List<WebhookDashboardDTO.RecentWebhookDTO> recentEvents =
                    getRecentWebhooks(20); // Last 20 events

            // Get failure analysis
            WebhookDashboardDTO.FailureAnalysisDTO failureAnalysis =
                    analyzeFailures();

            return WebhookDashboardDTO.builder()
                    .metrics(metrics)
                    .health(health)
                    .recentEvents(recentEvents)
                    .failureAnalysis(failureAnalysis)
                    .circuitBreakerStatus(new WebhookDashboardDTO.CircuitBreakerStatusDTO())
                    .build();

        } catch (Exception e) {
            log.error("Error fetching webhook dashboard", e);
            return WebhookDashboardDTO.builder()
                    .health(WebhookDashboardDTO.WebhookHealthDTO.builder()
                            .status("ERROR")
                            .message("Erro ao carregar dashboard de webhooks")
                            .build())
                    .build();
        }
    }

    /**
     * Get recent webhook events (latest N events with details)
     */
    public List<WebhookDashboardDTO.RecentWebhookDTO> getRecentWebhooks(int limit) {
        log.info("Fetching last {} webhook events", limit);

        Pageable pageable = PageRequest.of(0, limit);

        return eventLogRepository.findAll(pageable).stream()
                .map(event -> WebhookDashboardDTO.RecentWebhookDTO.builder()
                        .eventId(event.getEventId())
                        .eventType(event.getEventType())
                        .status(event.getStatus() != null ? event.getStatus().toString() : "UNKNOWN")
                        .retryCount(event.getRetryCount() != null ? event.getRetryCount() : 0)
                        .lastError(event.getLastError())
                        .processedAt(event.getProcessedAt())
                        .createdAt(event.getCreatedAt())
                        .tenantId(event.getTenantId() != null ? event.getTenantId().toString() : "public")
                        .customerId(event.getCustomerId())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Analyze webhook failures by reason
     */
    public WebhookDashboardDTO.FailureAnalysisDTO analyzeFailures() {
        log.info("Analyzing webhook failures");

        List<StripeEventLog> failedEvents = eventLogRepository
                .findByStatuses(List.of(StripeEventLog.EventProcessingStatus.FAILED));

        if (failedEvents.isEmpty()) {
            return WebhookDashboardDTO.FailureAnalysisDTO.builder()
                    .failureCount(0L)
                    .reasons(Collections.emptyList())
                    .analysisTime(LocalDateTime.now())
                    .build();
        }

        // Group failures by error type (extract key reason from error message)
        Map<String, Long> failureReasons = new HashMap<>();

        for (StripeEventLog event : failedEvents) {
            String reason = extractFailureReason(event.getLastError());
            failureReasons.merge(reason, 1L, Long::sum);
        }

        // Build response with percentages
        long totalFailures = failedEvents.size();
        List<WebhookDashboardDTO.FailureReasonDTO> reasonsList = failureReasons.entrySet()
                .stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> WebhookDashboardDTO.FailureReasonDTO.builder()
                        .reason(entry.getKey())
                        .count(entry.getValue())
                        .percentage((entry.getValue() * 100.0) / totalFailures)
                        .build())
                .collect(Collectors.toList());

        return WebhookDashboardDTO.FailureAnalysisDTO.builder()
                .failureCount(totalFailures)
                .reasons(reasonsList)
                .analysisTime(LocalDateTime.now())
                .build();
    }

    /**
     * Get webhook tenant breakdown (count by tenant)
     */
    public Map<String, Long> getWebhooksByTenant() {
        log.info("Fetching webhook breakdown by tenant");

        List<StripeEventLog> allEvents = eventLogRepository.findAll();

        return allEvents.stream()
                .collect(Collectors.groupingBy(
                        event -> event.getTenantId() != null
                                ? event.getTenantId().toString()
                                : "public",
                        Collectors.counting()
                ));
    }

    /**
     * Get webhook event type breakdown
     */
    public Map<String, Long> getWebhooksByEventType() {
        log.info("Fetching webhook breakdown by event type");

        List<StripeEventLog> allEvents = eventLogRepository.findAll();

        return allEvents.stream()
                .collect(Collectors.groupingBy(
                        StripeEventLog::getEventType,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Get webhook status breakdown
     */
    public Map<String, Long> getWebhooksByStatus() {
        log.info("Fetching webhook breakdown by status");

        List<StripeEventLog> allEvents = eventLogRepository.findAll();

        return allEvents.stream()
                .collect(Collectors.groupingBy(
                        event -> event.getStatus() != null
                                ? event.getStatus().toString()
                                : "UNKNOWN",
                        Collectors.counting()
                ));
    }

    // =====================================================
    // WEBHOOK HELPERS
    // =====================================================

    private WebhookDashboardDTO.WebhookHealthDTO buildWebhookHealth(
            long pendingCount,
            long successCount,
            long failureCount) {

        String status;
        String message;

        if (pendingCount > 100) {
            status = "CRITICAL";
            message = "Muitos eventos aguardando retry (" + pendingCount + ")";
        } else if (pendingCount > 50) {
            status = "WARNING";
            message = "Lote de eventos aguardando processamento (" + pendingCount + ")";
        } else if (failureCount > successCount / 2) {
            status = "WARNING";
            message = "Taxa de falha elevada";
        } else {
            status = "OK";
            message = "Sistema de webhooks operando normalmente";
        }

        LocalDateTime lastProcessed = eventLogRepository.findAll().stream()
                .map(StripeEventLog::getProcessedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime lastFailure = eventLogRepository
                .findByStatuses(List.of(StripeEventLog.EventProcessingStatus.FAILED)).stream()
                .map(StripeEventLog::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return WebhookDashboardDTO.WebhookHealthDTO.builder()
                .status(status)
                .message(message)
                .pendingRetryCount((int) pendingCount)
                .lastProcessedAt(lastProcessed)
                .lastFailureAt(lastFailure)
                .build();
    }

    private String extractFailureReason(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return "UNKNOWN";
        }

        // Extract key failure reasons from error messages
        if (errorMessage.contains("tenant")) return "TENANT_NOT_FOUND";
        if (errorMessage.contains("customer")) return "INVALID_CUSTOMER";
        if (errorMessage.contains("timeout")) return "TIMEOUT";
        if (errorMessage.contains("invalid") || errorMessage.contains("malformed"))
            return "INVALID_EVENT";
        if (errorMessage.contains("duplicate")) return "DUPLICATE_EVENT";
        if (errorMessage.contains("network") || errorMessage.contains("connection"))
            return "NETWORK_ERROR";
        if (errorMessage.contains("authentication") || errorMessage.contains("permission"))
            return "AUTH_ERROR";

        // Default to first 30 chars of error
        return errorMessage.substring(0, Math.min(30, errorMessage.length()));
    }

    private double calculateAverageProcessingTime() {
        List<StripeEventLog> processedEvents = eventLogRepository
                .findByStatuses(List.of(StripeEventLog.EventProcessingStatus.SUCCESS));

        if (processedEvents.isEmpty()) return 0.0;

        return processedEvents.stream()
                .map(event -> {
                    if (event.getCreatedAt() != null && event.getProcessedAt() != null) {
                        return ChronoUnit.MILLIS.between(event.getCreatedAt(), event.getProcessedAt());
                    }
                    return 0L;
                })
                .mapToDouble(Long::doubleValue)
                .average()
                .orElse(0.0);
    }
}