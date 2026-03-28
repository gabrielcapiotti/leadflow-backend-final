package com.leadflow.backend.controller.billing;

import com.leadflow.backend.dto.billing.BillingDashboardDTO;
import com.leadflow.backend.dto.billing.SubscriptionDetailsDTO;
import com.leadflow.backend.dto.billing.WebhookDashboardDTO;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.security.VendorContext;
import com.leadflow.backend.service.billing.BillingDashboardService;
import com.leadflow.backend.service.vendor.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingDashboardController {

    private final BillingDashboardService billingDashboardService;
    private final VendorContext vendorContext;
    private final SubscriptionService subscriptionService;

    // =====================================================
    // ADMIN ENDPOINTS (EXPLICIT TENANT)
    // =====================================================

    @GetMapping("/dashboard/{tenantId}")
    @PreAuthorize("@securityService.isTenantOwner(#tenantId)")
    public ResponseEntity<BillingDashboardDTO> getDashboard(@PathVariable UUID tenantId) {
        log.info("Fetching billing dashboard for tenant: {}", tenantId);
        return ResponseEntity.ok(billingDashboardService.getBillingDashboard(tenantId));
    }

    @GetMapping("/subscription/{tenantId}")
    @PreAuthorize("@securityService.isTenantOwner(#tenantId)")
    public ResponseEntity<SubscriptionDetailsDTO> getSubscriptionDetails(@PathVariable UUID tenantId) {
        log.info("Fetching subscription details for tenant: {}", tenantId);

        SubscriptionDetailsDTO details = billingDashboardService.getSubscriptionDetails(tenantId);

        if (details == null) {
            log.warn("Subscription not found for tenant: {}", tenantId);
            return ResponseEntity.ok(SubscriptionDetailsDTO.createEmptySubscription());
        }

        return ResponseEntity.ok(details);
    }

    @GetMapping("/events/{tenantId}")
    @PreAuthorize("@securityService.isTenantOwner(#tenantId)")
    public ResponseEntity<List<StripeEventLog>> getEventHistory(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("Fetching event history for tenant: {} (limit: {})", tenantId, limit);
        return ResponseEntity.ok(billingDashboardService.getEventHistory(tenantId, limit));
    }

    @GetMapping("/usage/{tenantId}")
    @PreAuthorize("@securityService.isTenantOwner(#tenantId)")
    public ResponseEntity<BillingDashboardDTO.UsageStatisticsDTO> getUsageStatistics(@PathVariable UUID tenantId) {
        log.info("Fetching usage statistics for tenant: {}", tenantId);

        BillingDashboardDTO.UsageStatisticsDTO usage =
                billingDashboardService.getUsageStatistics(tenantId);

        if (usage == null) {
            log.warn("Usage not found for tenant: {}", tenantId);
            return ResponseEntity.ok(new BillingDashboardDTO.UsageStatisticsDTO());
        }

        return ResponseEntity.ok(usage);
    }

    // =====================================================
    // SYSTEM HEALTH
    // =====================================================

    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillingHealthDTO> getHealth() {
        log.info("Fetching billing system health");

        boolean pendingRetries = billingDashboardService.hasPendingRetries();

        BillingHealthDTO health = BillingHealthDTO.builder()
                .status(pendingRetries ? "WARNING" : "OK")
                .hasPendingRetries(pendingRetries)
                .message(pendingRetries
                        ? "Sistema tem eventos pendentes de retry"
                        : "Todos os eventos processados com sucesso")
                .timestamp(java.time.LocalDateTime.now())
                .build();

        return ResponseEntity.ok(health);
    }

    // =====================================================
    // USER CONTEXT (NO tenantId)
    // =====================================================

    @GetMapping("/subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionDetailsDTO> getMySubscription() {

        UUID tenantId = resolveTenantSafe();

        if (tenantId == null) {
            log.warn("User has no vendor - returning empty subscription");
            return ResponseEntity.ok(SubscriptionDetailsDTO.createEmptySubscription());
        }

        try {
            SubscriptionDetailsDTO details =
                    billingDashboardService.getSubscriptionDetails(tenantId);

            if (details == null) {
                log.warn("Subscription null for tenant: {}", tenantId);
                return ResponseEntity.ok(SubscriptionDetailsDTO.createEmptySubscription());
            }

            return ResponseEntity.ok(details);

        } catch (Exception e) {
            log.error("Error fetching subscription for tenant: {}", tenantId, e);
            return ResponseEntity.ok(SubscriptionDetailsDTO.createEmptySubscription());
        }
    }

    @GetMapping("/usage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BillingDashboardDTO.UsageStatisticsDTO> getMyUsage() {

        UUID tenantId = resolveTenantSafe();

        if (tenantId == null) {
            log.warn("User has no vendor - returning empty usage");
            return ResponseEntity.ok(new BillingDashboardDTO.UsageStatisticsDTO());
        }

        try {
            BillingDashboardDTO.UsageStatisticsDTO usage =
                    billingDashboardService.getUsageStatistics(tenantId);

            if (usage == null) {
                return ResponseEntity.ok(new BillingDashboardDTO.UsageStatisticsDTO());
            }

            return ResponseEntity.ok(usage);

        } catch (Exception e) {
            log.error("Error fetching usage for tenant: {}", tenantId, e);
            return ResponseEntity.ok(new BillingDashboardDTO.UsageStatisticsDTO());
        }
    }

    @PostMapping("/subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createSubscription(@RequestBody Map<String, String> request) {

        String planId = request.get("planId");
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "message", "planId is required"
            ));
        }

        log.info("🔵 Creating subscription with plan: {}", planId);

        try {
            SubscriptionDetailsDTO subscription = billingDashboardService.createSubscription(planId);

            return ResponseEntity.ok(Map.of(
                    "status", "subscription_created",
                    "subscription", subscription,
                    "timestamp", java.time.LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to create subscription: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);

            return ResponseEntity.badRequest().body(Map.of(
                    "error", "subscription_creation_failed",
                    "message", e.getMessage()
            ));
        }
    }

    @PutMapping("/subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateSubscription(@RequestBody Map<String, String> request) {

        String planId = request.get("planId");
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "message", "planId is required"
            ));
        }

        log.info("🔵 Updating subscription to plan: {}", planId);

        try {
            SubscriptionDetailsDTO subscription = billingDashboardService.updateSubscription(planId);

            return ResponseEntity.ok(Map.of(
                    "status", "subscription_updated",
                    "subscription", subscription,
                    "timestamp", java.time.LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to update subscription: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);

            return ResponseEntity.badRequest().body(Map.of(
                    "error", "subscription_update_failed",
                    "message", e.getMessage()
            ));
        }
    }

    // =====================================================
    // ACTIONS
    // =====================================================

    @PostMapping("/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancelSubscription() {

        UUID tenantId = resolveTenantSafe();

        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "vendor_not_found",
                    "message", "User has no associated vendor"
            ));
        }

        log.warn("Cancelling subscription for tenant: {}", tenantId);

        try {
            subscriptionService.cancelSubscription(tenantId);

            return ResponseEntity.ok(Map.of(
                    "status", "subscription_cancelled",
                    "tenantId", tenantId.toString(),
                    "timestamp", java.time.LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Failed to cancel subscription for tenant: {}", tenantId, e);

            return ResponseEntity.badRequest().body(Map.of(
                    "error", "cancellation_failed",
                    "message", e.getMessage()
            ));
        }
    }

    // =====================================================
    // INTERNAL HELPER
    // =====================================================

    private UUID resolveTenantSafe() {
        try {
            String tenantString = com.leadflow.backend.multitenancy.context.TenantContext.getTenant();
            
            if (tenantString == null || tenantString.isBlank()) {
                log.warn("TenantContext returned null/blank tenant");
                return null;
            }
            
            return UUID.fromString(tenantString);
        } catch (Exception e) {
            log.error("Failed to resolve tenant from TenantContext", e);
            return null;
        }
    }

    // =====================================================
    // WEBHOOK DASHBOARD ENDPOINTS (ETAPA 2)
    // =====================================================

    @GetMapping("/webhooks/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookDashboardDTO> getWebhookDashboard() {
        log.info("Fetching webhook dashboard metrics");

        try {
            WebhookDashboardDTO dashboard = billingDashboardService.getWebhookDashboard();
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Error fetching webhook dashboard", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/webhooks/recent")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebhookDashboardDTO.RecentWebhookDTO>> getRecentWebhooks(
            @RequestParam(defaultValue = "20") int limit) {

        log.info("Fetching last {} recent webhooks", limit);

        try {
            List<WebhookDashboardDTO.RecentWebhookDTO> recentEvents =
                    billingDashboardService.getRecentWebhooks(Math.min(limit, 100));

            return ResponseEntity.ok(recentEvents);
        } catch (Exception e) {
            log.error("Error fetching recent webhooks", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/webhooks/failures")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookDashboardDTO.FailureAnalysisDTO> analyzeWebhookFailures() {
        log.info("Analyzing webhook failures");

        try {
            WebhookDashboardDTO.FailureAnalysisDTO analysis =
                    billingDashboardService.analyzeFailures();

            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Error analyzing webhook failures", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/webhooks/breakdown/by-tenant")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getWebhooksByTenant() {
        log.info("Fetching webhook breakdown by tenant");

        try {
            Map<String, Long> breakdown = billingDashboardService.getWebhooksByTenant();
            return ResponseEntity.ok(breakdown);
        } catch (Exception e) {
            log.error("Error fetching webhook breakdown by tenant", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/webhooks/breakdown/by-type")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getWebhooksByEventType() {
        log.info("Fetching webhook breakdown by event type");

        try {
            Map<String, Long> breakdown = billingDashboardService.getWebhooksByEventType();
            return ResponseEntity.ok(breakdown);
        } catch (Exception e) {
            log.error("Error fetching webhook breakdown by event type", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/webhooks/breakdown/by-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getWebhooksByStatus() {
        log.info("Fetching webhook breakdown by status");

        try {
            Map<String, Long> breakdown = billingDashboardService.getWebhooksByStatus();
            return ResponseEntity.ok(breakdown);
        } catch (Exception e) {
            log.error("Error fetching webhook breakdown by status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // =====================================================
    // DTO
    // =====================================================

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class BillingHealthDTO {
        private String status;
        private Boolean hasPendingRetries;
        private String message;
        private java.time.LocalDateTime timestamp;
    }
}