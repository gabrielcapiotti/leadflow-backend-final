package com.leadflow.backend.webhook.resolver;

import com.leadflow.backend.repository.StripeCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Clean abstraction for resolving tenant from webhook event data.
 * 
 * Priority order:
 * 1. metadata.tenantId (from Stripe event metadata) - PRIMARY & MOST RELIABLE
 * 2. customerId → StripeCustomer → Tenant lookup - SECONDARY
 * 3. null (unknown) - TERTIARY (will use global fallback store)
 * 
 * 🔥 CRITICAL: Only resolves tenant from DATA, never makes external Stripe API calls
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookTenantResolver {

    private final StripeCustomerRepository stripeCustomerRepository;

    /**
     * Resolve tenant UUID from webhook event metadata and data.
     * 
     * @param metadataTenantId The tenantId field from event.metadata (may be null)
     * @param customerId The customer ID from event.data.object.customer (may be null)
     * @return UUID of resolved tenant, or empty if cannot resolve
     * 
     * Example flow:
     * - Event comes with metadata.tenantId → SUCCESS (return it)
     * - Event has no metadata but has customerId → lookup in DB → return if found
     * - Event has neither → return empty (will use global fallback store)
     */
    public Optional<UUID> resolveTenant(String metadataTenantId, String customerId) {
        
        // STEP 1: Try metadata first (highest priority)
        if (metadataTenantId != null && !metadataTenantId.isBlank()) {
            try {
                UUID tenantUuid = UUID.fromString(metadataTenantId);
                log.info("[TENANT_RESOLVER] ✅ Resolved tenant from metadata: {}", tenantUuid);
                return Optional.of(tenantUuid);
            } catch (IllegalArgumentException e) {
                log.warn("[TENANT_RESOLVER] Invalid UUID format in metadata.tenantId: {}", metadataTenantId);
            }
        }

        // STEP 2: Try customer ID lookup (secondary)
        if (customerId != null && !customerId.isBlank() && !"unknown".equals(customerId)) {
            try {
                var stripeCustomer = stripeCustomerRepository.findByStripeCustomerId(customerId);
                if (stripeCustomer.isPresent() && stripeCustomer.get().getTenant() != null) {
                    UUID tenantUuid = stripeCustomer.get().getTenant().getId();
                    log.info("[TENANT_RESOLVER] ✅ Resolved tenant from customerId {}: {}", customerId, tenantUuid);
                    return Optional.of(tenantUuid);
                } else {
                    log.warn("[TENANT_RESOLVER] Customer {} not yet mapped in DB (retry later)", customerId);
                }
            } catch (Exception e) {
                log.error("[TENANT_RESOLVER] Error resolving tenant from customerId {}: {}", 
                    customerId, e.getMessage());
            }
        }

        // STEP 3: Cannot resolve
        log.warn("[TENANT_RESOLVER] ⚠️  Cannot resolve tenant from metadata or customerId (will use global fallback)");
        return Optional.empty();
    }

    /**
     * Validate that a tenant UUID is valid and not a system/unknown placeholder.
     * Rejects common invalid patterns.
     * 
     * @param tenantId The UUID to validate
     * @return true if valid tenant, false if null/invalid
     */
    public boolean isValidTenant(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }

        // Reject nil UUID (00000000-0000-0000-0000-000000000000)
        if (tenantId.equals(new UUID(0, 0))) {
            log.warn("[TENANT_RESOLVER] Rejected nil UUID as tenant");
            return false;
        }

        return true;
    }
}
