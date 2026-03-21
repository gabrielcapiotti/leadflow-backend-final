package com.leadflow.backend.service.billing;

import com.leadflow.backend.dto.billing.BillingDashboardDTO;
import com.leadflow.backend.dto.billing.SubscriptionDetailsDTO;
import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.entities.UsageLimit;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.repository.UsageLimitRepository;
import com.leadflow.backend.repository.PlanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

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
}