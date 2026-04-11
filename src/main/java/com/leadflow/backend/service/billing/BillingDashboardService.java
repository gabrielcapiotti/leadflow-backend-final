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
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingDashboardService {

    private final SubscriptionRepository subscriptionRepository;
    private final UsageLimitRepository usageLimitRepository;
    private final PlanRepository planRepository;
    private final StripeService stripeService;

    // =====================================================
    // ENTRYPOINT (PADRÃO CORRETO)
    // =====================================================

    @Transactional(readOnly = true)
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

        UUID tenant = TenantContext.getTenant();

        if (tenant == null) {
            throw new IllegalStateException("Tenant not found in context");
        }

        return tenant;
    }

    /**
     * Get tenant email from context or subscription record.
     * Creates a placeholder if email is not available.
     */
    private String getTenantEmailFromContext() {
        UUID tenantId = resolveTenant();
        
        // Try to get email from subscription record 
        Optional<Subscription> subscription = subscriptionRepository.findByTenantId(tenantId);
        if (subscription.isPresent() && subscription.get().getEmail() != null) {
            return subscription.get().getEmail();
        }
        
        // Fallback: use tentId@example.com (in production, get from user principal or context)
        log.warn("Email not found for tenant {} - using placeholder", tenantId);
        return null; // Return null to indicate email not available
    }

    // =====================================================
    // SUBSCRIPTION
    // =====================================================

    /**
     * Find subscription with Plan fetched (avoids LazyInitializationException).
     * Single source of truth: database only.
     */
    private Optional<Subscription> findSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantIdWithPlanFetch(tenantId);
    }

    // =====================================================
    // PUBLIC ADMIN METHODS (WITH EXPLICIT tenantId)
    // =====================================================

    @Transactional(readOnly = true)
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
        
        // Handle null usage stats
        if (usageStats == null) {
            usageStats = new BillingDashboardDTO.UsageStatisticsDTO();
        }
        
        boolean isActive = sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE;
        
        return BillingDashboardDTO.builder()
                .subscription(subscriptionDetails)
                .usageStatistics(usageStats)
                .hasActiveSubscription(isActive)
                .currentStatus(sub.getStatus().name())
                .nextAction(getNextAction(sub))
                .build();
    }

    @Transactional(readOnly = true)
    public SubscriptionDetailsDTO getSubscriptionDetails(UUID tenantId) {
        Optional<Subscription> subscription = findSubscription(tenantId);
        
        if (subscription.isEmpty()) {
            log.warn("No subscription details found for tenant {}", tenantId);
            return SubscriptionDetailsDTO.createEmptySubscription();
        }
        
        return SubscriptionDetailsDTO.fromEntity(subscription.get());
    }

    @Transactional(readOnly = true)
    public BillingDashboardDTO.UsageStatisticsDTO getUsageStatistics(UUID tenantId) {
        // READ-ONLY: No lock, safe for dashboards and reports
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

    @Transactional
    public BillingDashboardDTO.UsageStatisticsDTO getUsageStatisticsWithLock(UUID tenantId) {
        // WRITE-HEAVY: With pessimistic lock for concurrent writes
        UsageLimit usageLimit = usageLimitRepository.findByTenantIdForUpdate(tenantId).orElse(null);
        
        if (usageLimit == null || usageLimit.getPlan() == null) {
            log.warn("No usage data for tenant (locked): {}", tenantId);
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

    @Transactional(readOnly = true)
    public java.util.List<StripeEventLog> getEventHistory(UUID tenantId, int limit) {
        // Placeholder implementation - requires StripeEventLogRepository
        log.info("Fetching event history for tenant: {} (limit: {})", tenantId, limit);
        return new java.util.ArrayList<>();
    }

    @Transactional(readOnly = true)
    public boolean hasPendingRetries() {
        // Placeholder implementation
        log.info("Checking for pending retries");
        return false;
    }

    @Transactional(readOnly = true)
    public WebhookDashboardDTO getWebhookDashboard() {
        // Placeholder implementation
        log.info("Fetching webhook dashboard");
        return WebhookDashboardDTO.builder().build();
    }

    @Transactional(readOnly = true)
    public java.util.List<WebhookDashboardDTO.RecentWebhookDTO> getRecentWebhooks(int limit) {
        // Placeholder implementation
        log.info("Fetching recent webhooks (limit: {})", limit);
        return new java.util.ArrayList<>();
    }

    @Transactional(readOnly = true)
    public WebhookDashboardDTO.FailureAnalysisDTO analyzeFailures() {
        // Placeholder implementation
        log.info("Analyzing webhook failures");
        return WebhookDashboardDTO.FailureAnalysisDTO.builder().build();
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getWebhooksByTenant() {
        // Placeholder implementation
        log.info("Fetching webhook breakdown by tenant");
        return new java.util.HashMap<>();
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getWebhooksByEventType() {
        // Placeholder implementation
        log.info("Fetching webhook breakdown by event type");
        return new java.util.HashMap<>();
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getWebhooksByStatus() {
        // Placeholder implementation
        log.info("Fetching webhook breakdown by status");
        return new java.util.HashMap<>();
    }

    // =====================================================
    // SUBSCRIPTION MANAGEMENT (CREATE/UPDATE)
    // =====================================================

    @Transactional
    public SubscriptionDetailsDTO createSubscription(String planId) {
        UUID tenantId = resolveTenant();
        log.info("Creating subscription for tenant: {} with plan code: {}", tenantId, planId);
        
        // Fetch the plan by code (stable identifier)
        Plan plan = planRepository.findByCode(planId)
            .or(() -> planRepository.findByNameIgnoreCase(planId))  // Fallback to name for compatibility
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        
        // Check if subscription already exists
        Subscription existingSubscription = subscriptionRepository.findByTenantId(tenantId).orElse(null);
        
        Subscription subscription;
        if (existingSubscription != null) {
            // Update existing subscription
            existingSubscription.setPlan(plan);
            existingSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            
            // Call Stripe to update subscription if it has a Stripe ID
            if (existingSubscription.getStripeSubscriptionId() != null && 
                !existingSubscription.getStripeSubscriptionId().startsWith("session_")) {
                try {
                    log.info("Updating Stripe subscription: {}", existingSubscription.getStripeSubscriptionId());
                    // Stripe subscription update would happen here if needed
                } catch (Exception e) {
                    log.error("Failed to update Stripe subscription", e);
                    // Continue with local update
                }
            }
            
            subscription = subscriptionRepository.save(existingSubscription);
            log.info("Updated existing subscription for tenant: {}", tenantId);
        } else {
            // Create new subscription
            subscription = new Subscription();
            subscription.setTenantId(tenantId);
            subscription.setPlan(plan);
            subscription.setStatus(Subscription.SubscriptionStatus.TRIALING);
            
            // Try to create Stripe customer and subscription
            String stripeSubscriptionId = null;
            String stripeCustomerId = null;
            
            try {
                // Create or get Stripe customer using tenant context (if email available)
                // For now, use a placeholder - in production, get email from user context
                String tenantEmail = getTenantEmailFromContext();
                
                if (tenantEmail != null && !tenantEmail.isBlank()) {
                    // Create Stripe customer
                    com.stripe.model.Customer customer = stripeService.createCustomer(tenantEmail);
                    stripeCustomerId = customer.getId();
                    log.info("Created Stripe customer: {}", stripeCustomerId);
                    
                    // Create Stripe subscription
                    stripeSubscriptionId = stripeService.createSubscription(stripeCustomerId, plan.getCode(), tenantId);
                    log.info("Created Stripe subscription: {}", stripeSubscriptionId);
                    
                    subscription.setStripeCustomerId(stripeCustomerId);
                    subscription.setStripeSubscriptionId(stripeSubscriptionId);
                    subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                } else {
                    log.warn("Tenant email not available - Stripe subscription not created for tenant: {}", tenantId);
                }
            } catch (Exception e) {
                log.error("Failed to create Stripe subscription for tenant: {}", tenantId, e);
                // If Stripe fails, still save the local subscription but log the error
                // In production, consider retrying or queuing this operation
            }
            
            subscription = subscriptionRepository.save(subscription);
            log.info("Created new subscription for tenant: {}", tenantId);
        }
        
        return SubscriptionDetailsDTO.fromEntity(subscription);
    }

    @Transactional
    public SubscriptionDetailsDTO updateSubscription(String planId) {
        UUID tenantId = resolveTenant();
        log.info("Updating subscription for tenant: {} with plan code: {}", tenantId, planId);
        
        // Fetch the plan by code (stable identifier)
        Plan plan = planRepository.findByCode(planId)
            .or(() -> planRepository.findByNameIgnoreCase(planId))  // Fallback to name for compatibility
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        
        // Get existing subscription
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found for tenant: " + tenantId));
        
        // Update plan and status
        subscription.setPlan(plan);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription = subscriptionRepository.save(subscription);
        
        log.info("Successfully updated subscription for tenant: {} to plan code: {}", tenantId, planId);
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
            case TRIALING -> "Configurar pagamento";
            case ACTIVE -> "Nenhuma ação necessária";
            case PAST_DUE -> "Atualizar pagamento";
            case CANCELLED -> "Reativar assinatura";
            case INCOMPLETE -> "Completar pagamento";
            case COMPLETED -> "Assinatura finalizada";
        };
    }
}