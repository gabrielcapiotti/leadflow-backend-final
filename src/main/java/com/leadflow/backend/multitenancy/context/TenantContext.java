package com.leadflow.backend.multitenancy.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TenantContext {

    private static final Logger log =
            LoggerFactory.getLogger(TenantContext.class);

    /**
     * Regex para validar tenant identifiers - aceita UUIDs ou schema names PostgreSQL.
     * Padrões válidos:
     * - UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     * - Schema: deve iniciar com letra ou underscore, apenas alphanumeric + underscore
     */
    private static final Pattern VALID_TENANT =
            Pattern.compile("^([a-z_][a-z0-9_]{0,62}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");

    /**
     * Tenant padrão utilizado quando necessário.
     */
    private static final String DEFAULT_TENANT = "public";

    /**
     * ThreadLocal que mantém o tenant atual.
     */
    private static final ThreadLocal<String> CURRENT_TENANT =
            new ThreadLocal<>();

    private TenantContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    /* ======================================================
       SET
       ====================================================== */

    public static void setTenant(String tenant) {

        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant identifier cannot be null or blank"
            );
        }

        String normalized = tenant
                .trim()
                .toLowerCase(Locale.ROOT);

        // CRITICAL: Reject empty UUID
        if (normalized.equals("00000000-0000-0000-0000-000000000000")) {
            log.error("❌ CRITICAL: Attempt to set tenant to empty UUID (00000000-0000-0000-0000-000000000000)");
            log.error("   This indicates a fallback/conversion error. Check:");
            log.error("   1. CurrentTenantIdentifierResolver in Hibernate");
            log.error("   2. TenantFilter tenant resolution logic");
            log.error("   3. JWT tenant claim extraction");
            throw new IllegalArgumentException(
                    "Invalid tenant identifier: empty UUID detected. " +
                    "Tenants must be valid formatted identifiers (UUIDs or PostgreSQL schema names)"
            );
        }

        if (!VALID_TENANT.matcher(normalized).matches()) {

            log.error("Invalid tenant identifier received: {}", tenant);

            throw new IllegalArgumentException(
                    "Invalid tenant identifier: " + tenant + ". " +
                    "Must be UUID (xxxx-xxxx-...) or PostgreSQL schema name (public, tenant_xyz, etc)"
            );
        }

        // Idempotent: Allow setting the same tenant twice (silent return)
        // But prevent setting a DIFFERENT tenant (security check)
        String currentTenant = CURRENT_TENANT.get();
        if (currentTenant != null) {
            if (!currentTenant.equals(normalized)) {
                throw new IllegalStateException(
                        "Tenant already set for this thread. " +
                        "Current: " + currentTenant + ", " +
                        "Attempted: " + normalized
                );
            }
            // Same tenant already set - just return (idempotent)
            return;
        }

        CURRENT_TENANT.set(normalized);

        if (log.isDebugEnabled()) {
            log.debug("Tenant context set: {}", normalized);
        }
    }

    /* ======================================================
       SET IF ABSENT
       ====================================================== */

    public static void setIfAbsent(String tenant) {

        if (CURRENT_TENANT.get() == null) {
            setTenant(tenant);
        }
    }

    /* ======================================================
       GET STRICT
       ====================================================== */

    /**
     * Usado quando o tenant é obrigatório
     * (camada de domínio).
     */
    public static String getTenant() {

        String tenant = CURRENT_TENANT.get();

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
     * Alias explícito para uso em serviços.
     */
    public static String requireTenant() {
        return getTenant();
    }

    /* ======================================================
       GET OPTIONAL
       ====================================================== */

    /**
     * Usado por infraestrutura
     * (ex: Hibernate CurrentTenantIdentifierResolver).
     */
    public static String getIfPresent() {
        return CURRENT_TENANT.get();
    }

    /* ======================================================
       GET WITH FALLBACK
       ====================================================== */

    public static String getOrDefault() {

        String tenant = CURRENT_TENANT.get();

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

        String tenant = CURRENT_TENANT.get();

        if (tenant != null && log.isDebugEnabled()) {
            log.debug("Clearing tenant context: {}", tenant);
        }

        CURRENT_TENANT.remove();
    }
}