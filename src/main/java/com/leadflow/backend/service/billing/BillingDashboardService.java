package com.leadflow.backend.service.billing;

import com.leadflow.backend.dto.billing.BillingDashboardDTO;
import com.leadflow.backend.dto.billing.SubscriptionDetailsDTO;
import com.leadflow.backend.dto.billing.WebhookDashboardDTO;
import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.entities.UsageLimit;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.leadflow.backend.repository.UsageLimitRepository;
import com.leadflow.backend.repository.PlanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingDashboardService {

    private final SubscriptionRepository subscriptionRepository;
    private final UsageLimitRepository usageLimitRepository;
    private final PlanRepository planRepository;

    // =====================================================
    // ENTRYPOINT (PADRÃO CORRETO)
    // =====================================================

    public BillingDashboardDTO getBillingDashboardForCurrentTenant() {

        UUID tenantId = resolveTenant();

        Optional<Subscription> subscription = findSubscription(tenantId);

        if (subscription.isEmpty()) {
            log.warn("No subscription found for tenant {}", tenantId);
            return BillingDashboardDTO.builder()
                    .subscription(SubscriptionDetailsDTO.createEmptySubscription())
                    .usageStatistics(new BillingDashboardDTO.UsageStatisticsDTO())
                    .hasActiveSubscription(false)
                    .currentStatus("NO_SUBSCRIPTION")
                    .nextAction("CREATE_SUBSCRIPTION")
                    .build();
        }

        Subscription sub = subscription.get();
        SubscriptionDetailsDTO subscriptionDetails =
                SubscriptionDetailsDTO.fromEntity(sub);

        BillingDashboardDTO.UsageStatisticsDTO usageStats =
                getUsageStatistics(tenantId);

        boolean isActive =
                sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE;

        return BillingDashboardDTO.builder()
                .subscription(subscriptionDetails)
                .usageStatistics(usageStats)
                .hasActiveSubscription(isActive)
                .currentStatus(sub.getStatus().name())
                .nextAction(getNextAction(sub))
                .build();
    }

    // =====================================================
    // TENANT RESOLUTION (OBRIGATÓRIO)
    // =====================================================

    private UUID resolveTenant() {

        String tenant = TenantContext.getTenant();

        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("Tenant not found in context");
        }

        try {
            return UUID.fromString(tenant);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid tenant format: " + tenant);
        }
    }

    // =====================================================
    // SUBSCRIPTION
    // =====================================================

    /**
     * Find subscription without creating it.
     * Single source of truth: database only.
     */
    private Optional<Subscription> findSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId);
    }

    // =====================================================
    // PUBLIC ADMIN METHODS (WITH EXPLICIT tenantId)
    // =====================================================

    public BillingDashboardDTO getBillingDashboard(UUID tenantId) {
        Optional<Subscription> subscription = findSubscription(tenantId);
        
        if (subscription.isEmpty()) {
            log.warn("No subscription found for admin query - tenant {}", tenantId);
            return BillingDashboardDTO.builder()
                    .subscription(SubscriptionDetailsDTO.createEmptySubscription())
                    .usageStatistics(new BillingDashboardDTO.UsageStatisticsDTO())
                    .hasActiveSubscription(false)
                    .currentStatus("NO_SUBSCRIPTION")
                    .nextAction("CREATE_SUBSCRIPTION")
                    .build();
        }
        
        Subscription sub = subscription.get();
        SubscriptionDetailsDTO subscriptionDetails = SubscriptionDetailsDTO.fromEntity(sub);
        BillingDashboardDTO.UsageStatisticsDTO usageStats = getUsageStatistics(tenantId);
        boolean isActive = sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE;
        
        return BillingDashboardDTO.builder()
                .subscription(subscriptionDetails)
                .usageStatistics(usageStats)
                .hasActiveSubscription(isActive)
                .currentStatus(sub.getStatus().name())
                .nextAction(getNextAction(sub))
                .build();
    }

    public SubscriptionDetailsDTO getSubscriptionDetails(UUID tenantId) {
        Optional<Subscription> subscription = findSubscription(tenantId);
        
        if (subscription.isEmpty()) {
            log.warn("No subscription details found for tenant {}", tenantId);
            return SubscriptionDetailsDTO.createEmptySubscription();
        }
        
        return SubscriptionDetailsDTO.fromEntity(subscription.get());
    }

    public BillingDashboardDTO.UsageStatisticsDTO getUsageStatistics(UUID tenantId) {
        UsageLimit usageLimit = usageLimitRepository.findByTenantId(tenantId).orElse(null);
        
        if (usageLimit == null || usageLimit.getPlan() == null) {
            log.warn("No usage data for tenant: {}", tenantId);
            return null;
        }

        Plan plan = usageLimit.getPlan();
        long used = usageLimit.getLeadsUsed() != null ? usageLimit.getLeadsUsed() : 0L;
        long limit = plan.getMaxLeads() != null ? plan.getMaxLeads() : 0L;
        double percentage = (limit > 0) ? (used * 100.0) / limit : 0.0;

        return BillingDashboardDTO.UsageStatisticsDTO.builder()
                .leadsCreated(used)
                .leadsLimit(limit)
                .usagePercentage(percentage)
                .usageStatus(getUsageStatus(percentage))
                .build();
    }

    public java.util.List<StripeEventLog> getEventHistory(UUID tenantId, int limit) {
        // Placeholder implementation - requires StripeEventLogRepository
        log.info("Fetching event history for tenant: {} (limit: {})", tenantId, limit);
        return new java.util.ArrayList<>();
    }

    public boolean hasPendingRetries() {
        // Placeholder implementation
        log.info("Checking for pending retries");
        return false;
    }

    public WebhookDashboardDTO getWebhookDashboard() {
        // Placeholder implementation
        log.info("Fetching webhook dashboard");
        return WebhookDashboardDTO.builder().build();
    }

    public java.util.List<WebhookDashboardDTO.RecentWebhookDTO> getRecentWebhooks(int limit) {
        // Placeholder implementation
        log.info("Fetching recent webhooks (limit: {})", limit);
        return new java.util.ArrayList<>();
    }

    public WebhookDashboardDTO.FailureAnalysisDTO analyzeFailures() {
        // Placeholder implementation
        log.info("Analyzing webhook failures");
        return WebhookDashboardDTO.FailureAnalysisDTO.builder().build();
    }

    public java.util.Map<String, Long> getWebhooksByTenant() {
        // Placeholder implementation
        log.info("Fetching webhook breakdown by tenant");
        return new java.util.HashMap<>();
    }

    public java.util.Map<String, Long> getWebhooksByEventType() {
        // Placeholder implementation
        log.info("Fetching webhook breakdown by event type");
        return new java.util.HashMap<>();
    }

    public java.util.Map<String, Long> getWebhooksByStatus() {
        // Placeholder implementation
        log.info("Fetching webhook breakdown by status");
        return new java.util.HashMap<>();
    }

    // =====================================================
    // SUBSCRIPTION MANAGEMENT (CREATE/UPDATE)
    // =====================================================

    public SubscriptionDetailsDTO createSubscription(String planId) {
        UUID tenantId = resolveTenant();
        log.info("Creating subscription for tenant: {} with plan: {}", tenantId, planId);
        
        // Fetch the plan by ID or name
        Plan plan = planRepository.findByNameIgnoreCase(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        
        // Check if subscription already exists
        Subscription existingSubscription = subscriptionRepository.findByTenantId(tenantId).orElse(null);
        
        Subscription subscription;
        if (existingSubscription != null) {
            // Update existing subscription
            existingSubscription.setPlan(plan);
            existingSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            subscription = subscriptionRepository.save(existingSubscription);
            log.info("Updated existing subscription for tenant: {}", tenantId);
        } else {
            // Create new subscription
            subscription = new Subscription();
            subscription.setTenantId(tenantId);
            subscription.setPlan(plan);
            subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            subscription = subscriptionRepository.save(subscription);
            log.info("Created new subscription for tenant: {}", tenantId);
        }
        
        return SubscriptionDetailsDTO.fromEntity(subscription);
    }

    public SubscriptionDetailsDTO updateSubscription(String planId) {
        UUID tenantId = resolveTenant();
        log.info("Updating subscription for tenant: {} with plan: {}", tenantId, planId);
        
        // Fetch the plan by ID or name
        Plan plan = planRepository.findByNameIgnoreCase(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        
        // Get existing subscription
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found for tenant: " + tenantId));
        
        // Update plan and status
        subscription.setPlan(plan);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription = subscriptionRepository.save(subscription);
        
        log.info("Successfully updated subscription for tenant: {} to plan: {}", tenantId, planId);
        return SubscriptionDetailsDTO.fromEntity(subscription);
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

    private String getNextAction(Subscription subscription) {
        return switch (subscription.getStatus()) {
            case ACTIVE -> "Nenhuma ação necessária";
            case PAST_DUE -> "Atualizar pagamento";
            case CANCELLED -> "Reativar assinatura";
            case INCOMPLETE -> "Completar pagamento";
        };
    }
}