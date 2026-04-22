package com.leadflow.backend.service.vendor;

import com.leadflow.backend.config.BillingConfigService;
import com.leadflow.backend.config.converter.SafeUUIDDeserializer;
import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.entities.SubscriptionAudit;
import com.leadflow.backend.entities.vendor.SubscriptionAccessLevel;
import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.entities.vendor.VendorFeatureKey;
import com.leadflow.backend.exception.SubscriptionInactiveException;
import com.leadflow.backend.repository.SubscriptionAuditRepository;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.service.PlanService;
import com.leadflow.backend.service.notification.SubscriptionNotificationService;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class SubscriptionService {

    private final VendorRepository vendorRepository;
    private final SubscriptionAuditService auditService;
    private final VendorService vendorService;
    private final PlanService planService;
    private final UsageService usageService;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAuditRepository subscriptionAuditRepository;
    private final SubscriptionNotificationService notificationService;
    private final VendorFeatureService vendorFeatureService;
    private final BillingConfigService billingConfigService;

    public SubscriptionService(VendorRepository vendorRepository,
                               SubscriptionAuditService auditService,
                               VendorService vendorService,
                               PlanService planService,
                               UsageService usageService,
                               SubscriptionRepository subscriptionRepository,
                               SubscriptionAuditRepository subscriptionAuditRepository,
                               SubscriptionNotificationService notificationService,
                               VendorFeatureService vendorFeatureService,
                               BillingConfigService billingConfigService) {
        this.vendorRepository = vendorRepository;
        this.auditService = auditService;
        this.vendorService = vendorService;
        this.planService = planService;
        this.usageService = usageService;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAuditRepository = subscriptionAuditRepository;
        this.notificationService = notificationService;
        this.vendorFeatureService = vendorFeatureService;
        this.billingConfigService = billingConfigService;
        log.info("✅ SubscriptionService initialized - billing configured");
    }

    /* ======================================================
       ONBOARDING INITIALIZATION
       ====================================================== */

    /**
     * 🔥 CREATE DEFAULT SUBSCRIPTION FOR NEW TENANT (ONBOARDING)
     * 
     * Creates a subscription automatically when a user registers.
     * This is the ONLY way subscriptions are created at onboarding time.
     * 
     * Idempotent: if subscription already exists, returns it (no duplicate)
     * 
     * Usage:
     *   1. Tenant created
     *   2. Vendor created  
     *   3. ✅ createDefaultSubscription(tenantId) ← YOU ARE HERE
     *   4. Usage initialized
     * 
     * @param tenantId UUID of the newly created tenant
     * @return Subscription entity (newly created or existing)
     * @throws IllegalStateException if default plan not found
     */
    @Transactional
    public Subscription createDefaultSubscription(UUID tenantId) {

        log.info("Creating default subscription for tenant: {}", tenantId);
        
        //  IDEMPOTENCY: Avoid creating duplicate subscriptions
        Optional<Subscription> existing = subscriptionRepository.findByTenantId(tenantId);
        if (existing.isPresent()) {
            log.info("Subscription already exists for tenant: {}", tenantId);
            return existing.get();
        }

        // 🔥 Find default plan (should always be ONE active plan)
        Plan defaultPlan = planService.getActivePlan();

        if (defaultPlan == null) {
            log.error("❌ CRITICAL: No active plan found - cannot create subscription");
            throw new IllegalStateException(
                "Default plan not found - subscription creation failed. " +
                "Ensure at least one Plan with active=true exists in database."
            );
        }

        log.info("📋 Using plan: {} (id={})", defaultPlan.getName(), defaultPlan.getId());

        // 🔥 Create new subscription
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlan(defaultPlan);
        subscription.setStatus(Subscription.SubscriptionStatus.TRIALING);
        subscription.setStartedAt(LocalDateTime.now());
        
        // Trial period: 14 days from now
        subscription.setExpiresAt(LocalDateTime.now().plusDays(14));

        // 🔥 Save to database
        Subscription saved = subscriptionRepository.save(subscription);

        log.info("✅ Default subscription created: id={}, tenant={}, status={}, expires={}",
            saved.getId(), tenantId, saved.getStatus(), saved.getExpiresAt());

        // 🔥 ENABLE DEFAULT FEATURES FOR PLAN (Solução 1: Auto-enable features based on plan)
        enableDefaultFeaturesForPlan(tenantId, defaultPlan);

        return saved;
    }

    /**
     * 🎯 ENABLE DEFAULT FEATURES FOR PLAN
     * 
     * When a subscription is created, automatically enable features
     * that are included in the plan.
     * 
     * ✅ Uses EXPLICIT tenantId parameter (no TenantContext dependency)
     * 
     * Currently: All plans include all AI features by default
     * 
     * @param tenantId UUID of tenant (EXPLICIT - not from TenantContext)
     * @param plan Plan entity with included features
     */
    @Transactional
    private void enableDefaultFeaturesForPlan(UUID tenantId, Plan plan) {
        try {
            // Get vendor for this tenant
            List<Vendor> vendors = vendorRepository.findAllByTenantId(tenantId);
            
            if (vendors.isEmpty()) {
                log.warn("No vendors found for tenant {} - skipping feature enablement", tenantId);
                return;
            }
            
            // Get the primary vendor (first one)
            Vendor vendor = vendors.get(0);
            
            // All plans include all AI features by default
            // NOTE: Using SHORT names (AI_TITLE, AI_SENTIMENT, AI_GENERATE) 
            // because AiController verifies these specific names
            List<VendorFeatureKey> features = List.of(
                VendorFeatureKey.AI_CHAT,
                VendorFeatureKey.AI_SUMMARY,
                VendorFeatureKey.AI_TITLE,
                VendorFeatureKey.AI_REFINE,
                VendorFeatureKey.AI_SENTIMENT,
                VendorFeatureKey.AI_CLASSIFY,
                VendorFeatureKey.AI_GENERATE
            );
            
            log.info("🔧 Enabling {} features for vendor={}, tenant={}", 
                features.size(), vendor.getId(), tenantId);
            
            for (VendorFeatureKey featureKey : features) {
                try {
                    // ✅ NUEVO: Pass tenantId EXPLICITLY (not from TenantContext)
                    vendorFeatureService.upsertFeature(tenantId, vendor.getId(), featureKey, true);
                    
                    log.info("✅ Feature enabled: {} for vendor={}, tenant={}", 
                        featureKey, vendor.getId(), tenantId);
                    
                } catch (Exception e) {
                    log.warn("⚠️ Error enabling feature {} for vendor {} - {}", 
                        featureKey, vendor.getId(), e.getMessage());
                    // Continue with other features
                }
            }
            
            log.info("✅ All AI features enabled for subscription: plan={}, vendor={}, tenant={}",
                plan.getName(), vendor.getId(), tenantId);
                
        } catch (Exception e) {
            log.error("❌ Error enabling default features for plan: {}", plan.getName(), e);
            // Do NOT rethrow - let subscription creation succeed even if feature enablement fails
        }
    }
    

    /**
     * ⚠️ DEPRECATED: Vendor subscription status is no longer the source of truth
     * 
     * Use BillingService and the Subscription entity instead.
     * All subscription state must be in the subscriptions table.
     * 
     * @deprecated Vendor.subscriptionStatus is legacy - use Subscription entity only
     * @throws IllegalStateException Always
     */
    @Deprecated(forRemoval = true)
    public void transition(Subscription.SubscriptionStatus newStatus,
                           String reason,
                           String externalEventId) {
        log.error("❌ DEPRECATED: Subscription transition() called. Use BillingService only.");
        throw new IllegalStateException(
            "Subscription transitions via transition() are disabled. " +
            "Use BillingService and persistence methods for all state changes."
        );
    }

    /**
     * ⚠️ DEPRECATED: Access levels are now determined from Subscription entity only
     * 
     * @deprecated Use Subscription.getStatus() for access control
     * @throws IllegalStateException Always
     */
    @Deprecated(forRemoval = true)
    public SubscriptionAccessLevel getAccessLevel(Vendor vendor) {
        log.error("❌ DEPRECATED: Vendor.getAccessLevel() called. Use Subscription entity only.");
        throw new IllegalStateException(
            "Vendor-based access levels are disabled. Determine access from Subscription entity via BillingService."
        );
    }

    /**
     * ⚠️ DEPRECATED: Subscription expiration is now managed by Stripe webhooks only
     * 
     * All subscription state mutations (including expiration) must flow through
     * Stripe events processed by BillingService.
     * 
     * @deprecated Stripe webhook flow is the only source of truth
     * @throws IllegalStateException Always
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public void expireSubscriptions() {
        log.error("❌ DEPRECATED: Manual expireSubscriptions() called. Use Stripe webhook flow only.");
        throw new IllegalStateException(
            "Manual subscription expiration is disabled. " +
            "All subscription state must be synchronized via Stripe webhooks through BillingService."
        );
    }

    
    @Transactional
    public void activateAccount(Session session) {

        String stripeCustomerId = session.getCustomer();
        String stripeSubscriptionId = session.getSubscription();

        if (stripeCustomerId == null || stripeSubscriptionId == null) {
            log.warn("Invalid session: missing customer or subscription");
            return;
        }

        /*
         ----------------------------------------
         IDEMPOTENCY CHECK (prevent duplicate update)
         ----------------------------------------
         */

        Optional<Subscription> opt = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);

        if (opt.isPresent()) {
            Subscription existing = opt.get();
            
            // Already linked - idempotent
            if (existing.getStripeCustomerId() != null && 
                existing.getStripeCustomerId().equals(stripeCustomerId)) {
                log.info("Subscription already linked for stripeSubscriptionId={}", stripeSubscriptionId);
                return;
            }
        }

        /*
         ----------------------------------------
         EXTRACT TENANT ID
         ----------------------------------------
         */

        String tenantIdString = session.getMetadata().get("tenantId");

        if (tenantIdString == null) {
            log.error("Missing tenantId in Stripe checkout session metadata");
            return;
        }

        // Use safe UUID deserialization to detect corruption
        UUID tenantId = SafeUUIDDeserializer.deserialize(tenantIdString);

        log.info("Checkout completed for tenant: {}", tenantId);

        /*
         ----------------------------------------
         WAIT FOR SUBSCRIPTION (created by webhook)
         ----------------------------------------
         */

        Optional<Subscription> subscription = subscriptionRepository.findByTenantId(tenantId);

        if (subscription.isEmpty()) {
            log.warn("Subscription not found yet for tenant {}. Waiting for webhook to create it.", tenantId);
            return;
        }

        /*
         ----------------------------------------
         ONLY LINK STRIPE IDs
         ----------------------------------------
         */

        Subscription sub = subscription.get();
        
        // 🔥 DO NOT set status here - Stripe (webhook) is the source of truth
        
        sub.setStripeCustomerId(stripeCustomerId);
        sub.setStripeSubscriptionId(stripeSubscriptionId);

        log.info("Linking Stripe IDs only for tenant={}, stripeCustomerId={}, stripeSubscriptionId={}", 
                tenantId, stripeCustomerId, stripeSubscriptionId);

        subscriptionRepository.save(sub);

        log.info("Checkout linkage complete. Status will be set by Stripe webhook.");

    }

    @Transactional
    public void handlePaymentSucceeded(Event event) {
        if (event == null) {
            return;
        }

        log.info("Processing invoice.payment_succeeded event");

        Optional<Object> object = event.getDataObjectDeserializer().getObject().map(o -> (Object) o);
        if (object.isEmpty() || !(object.get() instanceof Invoice invoice)) {
            log.warn("Stripe invoice.payment_succeeded received without Invoice payload. eventId={}", event.getId());
            return;
        }

        String stripeSubscriptionId = invoice.getSubscription();

        Optional<Subscription> subscriptionOpt =
                subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);

        if (subscriptionOpt.isEmpty()) {
            log.warn("Subscription not found for renewal: {}", stripeSubscriptionId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        // Renovar período da assinatura
        LocalDateTime nextPeriod = LocalDateTime.now().plusMonths(1);

        subscription.setExpiresAt(nextPeriod);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);

        subscriptionRepository.save(subscription);

        log.info("Subscription renewed for tenant: {}", subscription.getTenantId());
    }

    @Transactional
    public void handleSubscriptionCancelled(Event event) {
        if (event == null) {
            return;
        }

        log.info("Processing customer.subscription.deleted event");

        Optional<Object> object = event.getDataObjectDeserializer().getObject().map(o -> (Object) o);
        if (object.isEmpty() || !(object.get() instanceof com.stripe.model.Subscription stripeSubscription)) {
            log.warn("Stripe customer.subscription.deleted received without Subscription payload. eventId={}", event.getId());
            return;
        }

        String stripeSubscriptionId = stripeSubscription.getId();

        Optional<Subscription> subscriptionOpt =
                subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);

        if (subscriptionOpt.isEmpty()) {
            log.warn("Subscription not found for cancellation: {}", stripeSubscriptionId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);

        subscriptionRepository.save(subscription);

        log.info("Subscription cancelled for tenant: {}", subscription.getTenantId());
    }

    @Transactional
    public void handlePaymentFailed(Event event) {
        if (event == null) {
            return;
        }

        log.info("Processing invoice.payment_failed event");

        Optional<Object> object = event.getDataObjectDeserializer().getObject().map(o -> (Object) o);
        if (object.isEmpty() || !(object.get() instanceof Invoice invoice)) {
            log.warn("Stripe invoice.payment_failed received without Invoice payload. eventId={}", event.getId());
            return;
        }

        String stripeSubscriptionId = invoice.getSubscription();

        Optional<Subscription> subscriptionOpt =
                subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);

        if (subscriptionOpt.isEmpty()) {
            log.warn("Subscription not found for payment failure: {}", stripeSubscriptionId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        // Atualizar status para PAST_DUE se ainda está ACTIVE
        if (subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE) {
            subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
            subscriptionRepository.save(subscription);
            
            log.warn("Payment failed - Subscription marked as PAST_DUE for tenant: {}", subscription.getTenantId());
        }
    }

    @Transactional
    public void handleSubscriptionUpdated(Event event) {
        if (event == null) {
            return;
        }

        log.info("Processing customer.subscription.updated event");

        Optional<Object> object = event.getDataObjectDeserializer().getObject().map(o -> (Object) o);
        if (object.isEmpty() || !(object.get() instanceof com.stripe.model.Subscription stripeSubscription)) {
            log.warn("Stripe customer.subscription.updated received without Subscription payload. eventId={}", event.getId());
            return;
        }

        String stripeSubscriptionId = stripeSubscription.getId();

        Optional<Subscription> subscriptionOpt =
                subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);

        if (subscriptionOpt.isEmpty()) {
            log.warn("Subscription not found for update: {}", stripeSubscriptionId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        // Sincronizar status principal
        String stripeStatus = stripeSubscription.getStatus();
        if ("active".equalsIgnoreCase(stripeStatus)) {
            subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        } else if ("past_due".equalsIgnoreCase(stripeStatus)) {
            subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
        } else if ("canceled".equalsIgnoreCase(stripeStatus)) {
            subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        }

        // Sincronizar plano se foi alterado
        Long currentPeriodEnd = stripeSubscription.getCurrentPeriodEnd();
        if (currentPeriodEnd != null) {
            LocalDateTime newExpiresAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(currentPeriodEnd),
                java.time.ZoneId.systemDefault()
            );
            subscription.setExpiresAt(newExpiresAt);
        }

        subscriptionRepository.save(subscription);

        log.info("Subscription updated for tenant: {} - Status: {}", 
            subscription.getTenantId(), subscription.getStatus());
    }

    @Transactional
    public void recordAuditTrail(Subscription subscription, 
                                  Subscription.SubscriptionStatus statusFrom,
                                  Subscription.SubscriptionStatus statusTo,
                                  String reason,
                                  String stripeEventId) {
        try {
            subscriptionAuditRepository.save(
                SubscriptionAudit.builder()
                    .subscriptionId(subscription.getId())
                    .tenantId(subscription.getTenantId())
                    .stripeSubscriptionId(subscription.getStripeSubscriptionId())
                    .statusFrom(statusFrom)
                    .statusTo(statusTo)
                    .reason(reason)
                    .stripeEventId(stripeEventId)
                    .build()
            );
            
            log.info("Audit trail recorded: {} -> {} for tenant {}", 
                statusFrom, statusTo, subscription.getTenantId());
        } catch (Exception e) {
            log.error("Failed to record audit trail", e);
        }
    }

    @Transactional
    public void notifyExpiringSubscriptions() {
        List<Subscription> expiringSubscriptions = subscriptionRepository
            .findByStatusAndExpiresAtBetween(
                Subscription.SubscriptionStatus.ACTIVE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
            );

        for (Subscription subscription : expiringSubscriptions) {
            try {
                notificationService.sendExpirationReminder(subscription);
                log.info("Expiration reminder sent for tenant: {}", subscription.getTenantId());
            } catch (Exception e) {
                log.error("Failed to send expiration reminder for tenant: {}", 
                    subscription.getTenantId(), e);
            }
        }
    }

    private Optional<Vendor> findVendorByStripeIds(String subscriptionId, String customerId) {
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            Optional<Vendor> bySubscription = vendorRepository.findByExternalSubscriptionId(subscriptionId);
            if (bySubscription.isPresent()) {
                return bySubscription;
            }
        }

        if (customerId != null && !customerId.isBlank()) {
            return vendorRepository.findByExternalCustomerId(customerId);
        }

        return Optional.empty();
    }

    /**
     * Validates if the subscription associated with the given tenant is active.
     * Throws SubscriptionInactiveException if subscription is not active.
     * 
     * In development mode (app.billing.enabled=false), validation is skipped.
     * 
     * @param tenantId the tenant ID to validate
     * @throws SubscriptionInactiveException if subscription does not exist or is not ACTIVE (only if billing is enabled)
     */
    public void validateActiveSubscription(UUID tenantId) {
        // Skip validation in development mode
        if (billingConfigService.isDisabled()) {
            log.debug("Billing validation skipped - development mode enabled");
            return;
        }

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
            .orElseThrow(() -> {
                log.warn("Subscription not found for tenant: {}", tenantId);
                return new SubscriptionInactiveException(
                    "Subscription not found for tenant: " + tenantId,
                    "SUBSCRIPTION_NOT_FOUND"
                );
            });

        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            log.warn("Subscription is not active. TenantId: {}, Status: {}", 
                tenantId, subscription.getStatus());
            throw new SubscriptionInactiveException(
                "Subscription is " + subscription.getStatus().name() + ". Expires at: " + subscription.getExpiresAt(),
                "SUBSCRIPTION_INACTIVE"
            );
        }

        log.debug("Subscription validation passed for tenant: {}", tenantId);
    }

    /**
     * Retrieves the subscription associated with the given tenant.
     * 
     * @param tenantId the tenant ID
     * @return Subscription details
     * @throws IllegalStateException if subscription not found
     */
    public Subscription getSubscriptionByTenant(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
            .orElseThrow(() -> {
                log.warn("Subscription not found for tenant: {}", tenantId);
                return new IllegalStateException(
                    String.format("Subscription not found for tenant: %s", tenantId)
                );
            });
    }

    /**
     * Cancels the subscription for a tenant by calling Stripe API.
     * Updates local subscription status to CANCELLED.
     * 
     * If subscription is local-only (no Stripe ID), cancels locally only.
     * 
     * @param tenantId the tenant ID
     * @throws RuntimeException if Stripe API call fails (for Stripe-managed subscriptions)
     */
    @Transactional
    public void cancelSubscription(UUID tenantId) {
        Subscription subscription = getSubscriptionByTenant(tenantId);

        try {
            // Check if subscription is managed by Stripe
            String stripeSubscriptionId = subscription.getStripeSubscriptionId();
            
            if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
                // Local-only subscription: cancel locally without calling Stripe
                log.warn("Subscription for tenant {} is local-only (no Stripe ID). Cancelling locally.", tenantId);
                subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
                subscriptionRepository.save(subscription);
                return;
            }

            // Stripe-managed subscription: cancel on Stripe first
            com.stripe.model.Subscription stripeSubscription = 
                com.stripe.model.Subscription.retrieve(stripeSubscriptionId);

            stripeSubscription.cancel();

            // Update local subscription status
            subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(subscription);

            // Record audit trail
            recordAuditTrail(
                subscription,
                Subscription.SubscriptionStatus.ACTIVE,
                Subscription.SubscriptionStatus.CANCELLED,
                "USER_REQUESTED_CANCELLATION",
                stripeSubscriptionId
            );

            log.info("Subscription cancelled for tenant: {} (Stripe: {})", tenantId, stripeSubscriptionId);

        } catch (com.stripe.exception.StripeException e) {
            log.error("Failed to cancel Stripe subscription for tenant: {}", tenantId, e);
            throw new RuntimeException("Stripe cancellation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Mark a payment as successful from webhook event.
     * Called when invoice.payment_succeeded webhook is received.
     * 
     * @param stripeSubscriptionId the Stripe subscription ID
     * @param invoiceId the Stripe invoice ID
     */
    @Transactional
    public void markPaymentSuccessful(String stripeSubscriptionId, String invoiceId) {
        try {
            Subscription subscription = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> {
                    log.warn("Subscription not found for Stripe ID: {}", stripeSubscriptionId);
                    return new IllegalArgumentException("Subscription not found: " + stripeSubscriptionId);
                });

            subscription.setLastPaymentDate(LocalDateTime.now());
            subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);

            recordAuditTrail(
                subscription,
                subscription.getStatus(),
                Subscription.SubscriptionStatus.ACTIVE,
                "PAYMENT_SUCCESSFUL",
                invoiceId
            );

            log.info("✅ Marked payment successful: stripeSubId={}, invoiceId={}", stripeSubscriptionId, invoiceId);

        } catch (Exception e) {
            log.error("❌ Error marking payment successful", e);
            throw new RuntimeException("Failed to mark payment successful", e);
        }
    }

    /**
     * Mark a subscription as deleted from Stripe.
     * Called when customer.subscription.deleted webhook is received.
     * 
     * @param stripeSubscriptionId the Stripe subscription ID
     */
    @Transactional
    public void markAsDeletedFromStripe(String stripeSubscriptionId) {
        try {
            Subscription subscription = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> {
                    log.warn("Subscription not found for Stripe ID: {}", stripeSubscriptionId);
                    return new IllegalArgumentException("Subscription not found: " + stripeSubscriptionId);
                });

            Subscription.SubscriptionStatus previousStatus = subscription.getStatus();
            subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            subscription.setCancelledAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);

            recordAuditTrail(
                subscription,
                previousStatus,
                Subscription.SubscriptionStatus.CANCELLED,
                "DELETED_FROM_STRIPE",
                stripeSubscriptionId
            );

            log.info("✅ Marked subscription as deleted: stripeSubId={}", stripeSubscriptionId);

        } catch (Exception e) {
            log.error("❌ Error marking subscription as deleted", e);
            throw new RuntimeException("Failed to mark subscription as deleted", e);
        }
    }

    /**
     * Sync subscription state with data from Stripe.
     * Called when customer.subscription.updated webhook is received.
     * 
     * @param stripeSubscription the Stripe subscription object
     */
    @Transactional
    public void syncWithStripe(com.stripe.model.Subscription stripeSubscription) {
        try {
            String stripeSubscriptionId = stripeSubscription.getId();
            
            Subscription subscription = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> {
                    log.warn("Subscription not found for Stripe ID: {}", stripeSubscriptionId);
                    return new IllegalArgumentException("Subscription not found: " + stripeSubscriptionId);
                });

            Subscription.SubscriptionStatus previousStatus = subscription.getStatus();

            // Sync status from Stripe
            String stripeStatus = stripeSubscription.getStatus();
            Subscription.SubscriptionStatus newStatus = switch (stripeStatus) {
                case "active" -> Subscription.SubscriptionStatus.ACTIVE;
                case "past_due" -> Subscription.SubscriptionStatus.PAST_DUE;
                case "canceled", "cancelled" -> Subscription.SubscriptionStatus.CANCELLED;
                case "incomplete" -> Subscription.SubscriptionStatus.INCOMPLETE;
                case "incomplete_expired" -> Subscription.SubscriptionStatus.INCOMPLETE;
                default -> previousStatus;
            };

            subscription.setStatus(newStatus);

            // Sync period dates
            if (stripeSubscription.getCurrentPeriodStart() != null) {
                subscription.setStartedAt(
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodStart()),
                        java.time.ZoneId.systemDefault()
                    )
                );
            }

            if (stripeSubscription.getCurrentPeriodEnd() != null) {
                subscription.setExpiresAt(
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()),
                        java.time.ZoneId.systemDefault()
                    )
                );
            }

            subscriptionRepository.save(subscription);

            if (previousStatus != newStatus) {
                recordAuditTrail(
                    subscription,
                    previousStatus,
                    newStatus,
                    "SYNCED_FROM_STRIPE",
                    stripeSubscriptionId
                );
                log.info("✅ Synced subscription with Stripe: stripeSubId={}, status: {} → {}", 
                    stripeSubscriptionId, previousStatus, newStatus);
            } else {
                log.debug("Subscription in sync with Stripe: stripeSubId={}", stripeSubscriptionId);
            }

        } catch (Exception e) {
            log.error("❌ Error syncing subscription with Stripe", e);
            throw new RuntimeException("Failed to sync subscription with Stripe", e);
        }
    }

    /**
     * Get subscription by vendor ID
     * First tries to find a Subscription entity in the subscriptions table.
     * If not found, builds one from the Vendor trial/subscription fields.
     * This supports both the Subscription entity pattern and the Vendor embedded pattern.
     * 
     * @param vendorId the vendor ID (same as tenantId)
     * @return Optional containing subscription if found
     */
    public Optional<Subscription> getSubscriptionByVendorId(UUID vendorId) {
        // Single source of truth: database repository only
        return subscriptionRepository.findByTenantId(vendorId);
    }

    /**
     * Get subscription by tenant ID (replaces Vendor-based access resolution)
     * Used by SubscriptionGuard to determine access levels for multi-tenant operations.
     * 
     * @param tenantId the tenant ID
     * @return Optional containing the Subscription entity if found
     */
    public Optional<Subscription> getSubscriptionByTenantId(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId);
    }

    /**
     * ⚠️ DEPRECATED: Manual subscription creation is disabled
     * 
     * Use ONLY Stripe-driven flow via BillingService.
     * All subscriptions must be created through Stripe checkout.
     * 
     * @deprecated Use BillingService for all subscription writes
     * @throws IllegalStateException Always
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public Optional<Subscription> createOrUpdateSubscription(UUID vendorId, String planId) {
        log.error("❌ DEPRECATED: Manual subscription creation called for vendorId={}. Use Stripe flow only.", vendorId);
        throw new IllegalStateException(
            "Manual subscription creation is disabled. " +
            "All subscriptions must be created through Stripe checkout flow via BillingService."
        );
    }
}
