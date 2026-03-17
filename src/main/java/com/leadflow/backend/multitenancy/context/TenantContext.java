package com.leadflow.backend.multitenancy.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TenantContext {

    private static final Logger log =
            LoggerFactory.getLogger(TenantContext.class);

    /**
     * Regex segura para schema PostgreSQL.
     * Deve iniciar com letra ou underscore.
     */
    private static final Pattern VALID_SCHEMA =
            Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

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

        if (!VALID_SCHEMA.matcher(normalized).matches()) {

            log.error("Invalid tenant identifier received: {}", tenant);

            throw new IllegalArgumentException(
                    "Invalid tenant identifier: " + tenant
            );
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
                    "No tenant set in current thread"
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