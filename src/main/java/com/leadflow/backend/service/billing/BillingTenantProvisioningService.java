package com.leadflow.backend.service.billing;

import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.entities.payment.Payment;
import com.leadflow.backend.entities.vendor.SubscriptionStatus;
import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.entities.vendor.VendorFeatureKey;

import com.leadflow.backend.repository.PaymentCheckoutRequestRepository;
import com.leadflow.backend.repository.PaymentRepository;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.service.PlanService;
import com.leadflow.backend.service.user.UserService;
import com.leadflow.backend.service.vendor.QuotaService;
import com.leadflow.backend.service.vendor.SubscriptionService;
import com.leadflow.backend.service.vendor.UsageService;
import com.leadflow.backend.service.vendor.VendorFeatureService;
import com.leadflow.backend.service.vendor.VendorService;
import com.stripe.model.checkout.Session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class BillingTenantProvisioningService {

    private final VendorService vendorService;
    private final UserService userService;
    private final VendorRepository vendorRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventService paymentEventService;
    private final PaymentCheckoutRequestRepository checkoutRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final VendorFeatureService vendorFeatureService;
    private final QuotaService quotaService;
    private final PlanService planService;
    private final UsageService usageService;

    public BillingTenantProvisioningService(
            VendorService vendorService,
            UserService userService,
            VendorRepository vendorRepository,
            PaymentRepository paymentRepository,
            PaymentEventService paymentEventService,
            PaymentCheckoutRequestRepository checkoutRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionService subscriptionService,
            VendorFeatureService vendorFeatureService,
            QuotaService quotaService,
            PlanService planService,
            UsageService usageService
    ) {
        this.vendorService = Objects.requireNonNull(vendorService);
        this.userService = Objects.requireNonNull(userService);
        this.vendorRepository = Objects.requireNonNull(vendorRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.paymentEventService = Objects.requireNonNull(paymentEventService);
        this.checkoutRepository = Objects.requireNonNull(checkoutRepository);
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository);
        this.vendorFeatureService = Objects.requireNonNull(vendorFeatureService);
        this.quotaService = Objects.requireNonNull(quotaService);
        this.planService = Objects.requireNonNull(planService);
        this.usageService = Objects.requireNonNull(usageService);
    }

    @Transactional
    public Vendor provisionFromCheckout(String eventId, Session session, String payload) {

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Stripe event id cannot be blank");
        }

        if (session == null) {
            throw new IllegalArgumentException("Stripe session cannot be null");
        }

        if (!paymentEventService.registerIfFirstProcess(eventId, "stripe")) {
            return findExistingVendorByEmail(extractEmail(session));
        }

        String email = extractEmail(session);
        String plan = extractPlan(session);
        String referenceId = extractReferenceId(session);
        String paymentStatus = resolvePaymentStatus(session);
        UUID tenantId = extractTenantId(session);

        savePayment(eventId, email, paymentStatus, payload);

        Vendor vendor = vendorRepository
                .findFirstByUserEmailIgnoreCase(email)
                .orElseGet(() -> vendorService.createVendor(email));

        if (!isPaymentConfirmed(paymentStatus)) {
            upsertSubscriptionRecord(session, vendor, email, plan, paymentStatus, tenantId);
            return vendor;
        }

        ensureSchema(vendor);
        userService.createAdminUser(vendor, email);

        if (tenantId == null) {
            throw new IllegalStateException("TenantId not found in Stripe session metadata for email: " + email);
        }
        activateVendor(vendor, session, eventId);
        applyPlan(vendor, tenantId, plan);
        registerLimits(vendor);
        initializeUsageLimits(vendor);
        completePendingCheckout(referenceId, plan);
        upsertSubscriptionRecord(session, vendor, email, plan, paymentStatus, tenantId);

        return vendor;
    }

    @Transactional
    public Vendor provision(String tenantIdentifier) {

        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            throw new IllegalArgumentException("Tenant identifier cannot be empty");
        }

        Vendor vendor = vendorRepository
                .findFirstByUserEmailIgnoreCase(tenantIdentifier)
                .orElseGet(() -> vendorService.createVendor(tenantIdentifier));

        ensureSchema(vendor);
        userService.createAdminUser(vendor, tenantIdentifier);
        registerLimits(vendor);

        return vendor;
    }

    private String extractEmail(Session session) {
        String email = session.getCustomerEmail();
        if (email == null || email.isBlank()) {
            Map<String, String> metadata = session.getMetadata();
            email = metadata != null ? metadata.get("email") : null;
        }

        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Stripe session missing customer email");
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String extractPlan(Session session) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null) {
            return "default";
        }

        String plan = metadata.get("plan");
        if (plan == null || plan.isBlank()) {
            return "default";
        }

        return plan.trim().toLowerCase(Locale.ROOT);
    }

    private String extractReferenceId(Session session) {
        Map<String, String> metadata = session.getMetadata();
        return metadata == null ? null : metadata.get("referenceId");
    }

    private UUID extractTenantId(Session session) {
        if (session == null || session.getMetadata() == null) {
            return null;
        }

        String tenantIdStr = session.getMetadata().get("tenantId");
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenantId format in Stripe session metadata: {}", tenantIdStr);
            return null;
        }
    }

    private String resolvePaymentStatus(Session session) {
        String paymentStatus = session.getPaymentStatus();
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            return paymentStatus.toLowerCase(Locale.ROOT);
        }

        String status = session.getStatus();
        return status == null ? "unknown" : status.toLowerCase(Locale.ROOT);
    }

    private void savePayment(String eventId, String email, String status, String payload) {
        if (paymentRepository.existsByEventId(eventId)) {
            return;
        }

        Payment payment = new Payment();
        payment.setEventId(eventId);
        payment.setEmail(email);
        payment.setStatus(status);
        payment.setGateway("stripe");
        payment.setPayload(payload);

        paymentRepository.save(payment);
    }

    private void ensureSchema(Vendor vendor) {
        // Schema creation is no longer tied to Vendor
        // Tenant schema should be initialized when Tenant is created (AuthController)
        // This method is kept for reference but not needed in current architecture
    }

    private void activateVendor(Vendor vendor, Session session, String eventId) {
        // ⚠️ DEPRECATED: Vendor subscription status transitions disabled
        // Use Subscription entity via BillingService instead
        vendor.setSubscriptionStatus(SubscriptionStatus.ATIVA);
        vendorRepository.save(vendor);

        if (vendor.getSubscriptionStartedAt() == null) {
            vendor.setSubscriptionStartedAt(Instant.now());
        }

        vendor.setLastPaymentAt(Instant.now());
        vendor.setExternalCustomerId(session.getCustomer());
        vendor.setExternalSubscriptionId(session.getSubscription());
        vendorRepository.save(vendor);
    }

    private void applyPlan(Vendor vendor, UUID tenantId, String plan) {
        // Plano "default" e "pro" habilitam IA no cenário atual.
        if (tenantId != null) {
            // Set tenant context for feature service
            TenantContext.setTenant(tenantId);
            try {
                vendorFeatureService.upsertFeature(vendor.getId(), VendorFeatureKey.AI_CHAT, true);
            } finally {
                TenantContext.clear();
            }
        } else {
            log.warn("⚠️ tenantId not found in Stripe session metadata - skipping feature setup for vendor={}", vendor.getId());
        }
    }

    private void registerLimits(Vendor vendor) {
        quotaService.initializePlanLimits(vendor.getId());
    }

    private void initializeUsageLimits(Vendor vendor) {
        Plan activePlan = planService.getActivePlan();
        usageService.initializeUsage(vendor.getId(), activePlan);
    }

    private void completePendingCheckout(String referenceId, String plan) {
        if (referenceId == null || referenceId.isBlank()) {
            return;
        }

        checkoutRepository.findTopByReferenceIdOrderByCreatedAtDesc(referenceId)
                .ifPresent(checkout -> {
                    checkout.setStatus("COMPLETED");
                    checkoutRepository.save(checkout);
                });
    }

    private void upsertSubscriptionRecord(Session session, Vendor vendor, String email, String plan, String status, UUID tenantId) {

        String stripeSubscriptionId = session.getSubscription();
        String stripeCustomerId = session.getCustomer();
        String fallbackSubscriptionId = "session_" + session.getId();

        Subscription subscription = null;

        if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            subscription = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).orElse(null);
        }

        if (subscription == null && stripeCustomerId != null && !stripeCustomerId.isBlank()) {
            subscription = subscriptionRepository.findByStripeCustomerId(stripeCustomerId).orElse(null);
        }

        if (subscription == null) {
            subscription = subscriptionRepository.findByEmailIgnoreCase(email).orElse(new Subscription());
        }

        // Definir tenant_id (relacionamento com Tenant, não Vendor)
        // CRÍTICO: usar o tenantId do webhook, não o vendorId
        subscription.setTenantId(tenantId);
        subscription.setEmail(email);
        
        // Usar Plan entity ao invés de String
        Plan activePlan = planService.getActivePlan();
        subscription.setPlan(activePlan);
        
        // Converter status String para enum SubscriptionStatus
        subscription.setStatus(mapToSubscriptionStatus(status));
        
        subscription.setStripeCustomerId(stripeCustomerId != null ? stripeCustomerId : email);
        subscription.setStripeSubscriptionId(stripeSubscriptionId != null ? stripeSubscriptionId : fallbackSubscriptionId);
        
        // Definir started_at se for nova assinatura
        if (subscription.getStartedAt() == null && isPaymentConfirmed(status)) {
            subscription.setStartedAt(LocalDateTime.now());
        }
        
        // expires_at pode ser definido futuramente quando tivermos dados do Stripe sobre período

        subscriptionRepository.save(subscription);
    }
    
    /**
     * Converte status String do Stripe para enum SubscriptionStatus.
     */
    private Subscription.SubscriptionStatus mapToSubscriptionStatus(String stripeStatus) {
        if (stripeStatus == null || stripeStatus.isBlank()) {
            return Subscription.SubscriptionStatus.ACTIVE;
        }
        
        String normalized = stripeStatus.toLowerCase(Locale.ROOT);
        
        if ("paid".equals(normalized) || "complete".equals(normalized) || "no_payment_required".equals(normalized) || "active".equals(normalized)) {
            return Subscription.SubscriptionStatus.ACTIVE;
        }
        
        if ("past_due".equals(normalized) || "unpaid".equals(normalized)) {
            return Subscription.SubscriptionStatus.PAST_DUE;
        }
        
        if ("canceled".equals(normalized) || "cancelled".equals(normalized) || "incomplete_expired".equals(normalized)) {
            return Subscription.SubscriptionStatus.CANCELLED;
        }
        
        // Default para qualquer outro status
        return Subscription.SubscriptionStatus.ACTIVE;
    }

    private boolean isPaymentConfirmed(String paymentStatus) {
        if (paymentStatus == null || paymentStatus.isBlank()) {
            return false;
        }

        return "paid".equalsIgnoreCase(paymentStatus)
                || "no_payment_required".equalsIgnoreCase(paymentStatus);
    }

    private Vendor findExistingVendorByEmail(String email) {
        return vendorRepository
                .findFirstByUserEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Duplicate Stripe event without vendor for email: " + email));
    }
}
