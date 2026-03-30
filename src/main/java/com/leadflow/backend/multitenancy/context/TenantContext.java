package com.leadflow.backend.multitenancy.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TenantContext {

    private static final Logger log =
            LoggerFactory.getLogger(TenantContext.class);

    /**
     * Regex para validar tenant identifiers - aceita UUIDs.
     * Padrão válido:
     * - UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     */
    private static final Pattern VALID_TENANT =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /**
     * Tenant padrão (public) - como UUID
     * Este é um tenant especial para contextos públicos
     */
    private static final java.util.UUID DEFAULT_TENANT = 
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * ThreadLocal que mantém o tenant atual como UUID.
     * NUNCA como String - sempre UUID para evitar manipulação indevida.
     */
    private static final ThreadLocal<java.util.UUID> CURRENT_TENANT =
            new ThreadLocal<>();

    private TenantContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    /* ======================================================
       SET
       ====================================================== */

    /**
     * Set tenant as UUID (MANDATORY)
     * 
     * RULE: tenantId is ALWAYS UUID, never String.
     * If you have a String tenant, parse it to UUID before calling this.
     * 
     * @param tenantId UUID tenant identifier (NOT nullable)
     * @throws IllegalArgumentException if null
     * @throws IllegalStateException if different tenant already set for this thread
     */
    public static void setTenant(java.util.UUID tenantId) {

        if (tenantId == null) {
            throw new IllegalArgumentException(
                    "Tenant identifier cannot be null - must be a valid UUID"
            );
        }

        // Idempotent: Allow setting the same tenant twice (silent return)
        // But prevent setting a DIFFERENT tenant (security check)
        java.util.UUID currentTenant = CURRENT_TENANT.get();
        if (currentTenant != null) {
            if (!currentTenant.equals(tenantId)) {
                throw new IllegalStateException(
                        String.format(
                                "Tenant already set for this thread. Current: %s, Attempted: %s",
                                currentTenant,
                                tenantId
                        )
                );
            }
            // Same tenant already set - just return (idempotent)
            return;
        }

        CURRENT_TENANT.set(tenantId);
        
        // Set tenant in MDC for logging (after validation)
        MDC.put("tenant_id", tenantId.toString());

        if (log.isDebugEnabled()) {
            log.debug("Tenant context set: {} → MDC", tenantId);
        }
    }

    /* ======================================================
       SET IF ABSENT
       ====================================================== */

    public static void setIfAbsent(java.util.UUID tenantId) {

        if (CURRENT_TENANT.get() == null) {
            setTenant(tenantId);
        }
    }

    /* ======================================================
       GET STRICT
       ====================================================== */

    /**
     * Get current tenant UUID (MANDATORY - throws if not set)
     * 
     * @return Current tenant UUID
     * @throws IllegalStateException if no tenant set in thread
     */
    public static java.util.UUID getTenant() {

        java.util.UUID tenant = CURRENT_TENANT.get();

        if (tenant == null) {
            throw new IllegalStateException(
                    "Tenant not set in current thread. " +
                    "Possible causes: filter misconfiguration, " +
                    "missing X-Tenant-Id header, or JWT missing tenant claim"
            );
        }

        return tenant;
    }

    /* ======================================================
       REQUIRE TENANT
       ====================================================== */

    /**
     * Alias for explicit tenant requirement in services
     */
    public static java.util.UUID requireTenant() {
        return getTenant();
    }

    /* ======================================================
       GET OPTIONAL
       ====================================================== */

    /**
     * Get current tenant UUID if present, null otherwise
     */
    public static java.util.UUID getIfPresent() {
        return CURRENT_TENANT.get();
    }

    /* ======================================================
       GET WITH FALLBACK
       ====================================================== */

    public static java.util.UUID getOrDefault() {

        java.util.UUID tenant = CURRENT_TENANT.get();

        if (tenant == null) {

            if (log.isDebugEnabled()) {
                log.debug(
                        "Tenant context empty. Using default schema: {}",
                        DEFAULT_TENANT
                );
            }

            return DEFAULT_TENANT;
        }

        return tenant;
    }

    /* ======================================================
       STATE
       ====================================================== */

    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    /* ======================================================
       CLEAR
       ====================================================== */

    /**
     * Deve ser chamado no finally do TenantFilter
     * para evitar vazamento entre requisições.
     */
    public static void clear() {

        java.util.UUID tenant = CURRENT_TENANT.get();

        if (tenant != null && log.isDebugEnabled()) {
            log.debug("Clearing tenant context: {}", tenant);
        }

        CURRENT_TENANT.remove();
        MDC.remove("tenant_id");  // Clean up MGC to prevent thread pool leaks
    }
}