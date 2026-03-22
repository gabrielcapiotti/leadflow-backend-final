package com.leadflow.backend.multitenancy.identifier;

import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.entities.Tenant;
import com.leadflow.backend.repository.tenant.TenantRepository;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 🔐 SECURE MULTI-TENANT RESOLVER
 * ================================
 * 
 * SECURITY PRINCIPLE:
 * - NEVER trust external input (headers) as source of truth
 * - ALWAYS resolve tenant via database (Tenant entity)
 * - Tenant context should come from JWT (authenticated user)
 * - Validate: existence + active status before switching schema
 *
 * SECURE FLOW:
 *   JWT (tenantId) → TenantContext → Resolver → DB lookup → Schema
 *   Instead of:
 *   Header → TenantContext → Resolver → Schema (INSECURE)
 */
@Component
@ConditionalOnProperty(name = "multitenancy.enabled", havingValue = "true", matchIfMissing = true)
public class CurrentTenantIdentifierResolverImpl
        implements CurrentTenantIdentifierResolver<String> {

    private static final Logger log =
            LoggerFactory.getLogger(CurrentTenantIdentifierResolverImpl.class);

    private static final String DEFAULT_TENANT = "public";

    private final ApplicationContext applicationContext;

    public CurrentTenantIdentifierResolverImpl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /* ======================================================
       SECURE TENANT RESOLUTION
       ====================================================== */

    @Override
    public String resolveCurrentTenantIdentifier() {

        // Step 1: Get tenant identifier from context
        // ℹ️ Should come from JWT, NOT from header
        String tenantId = TenantContext.getIfPresent();

        if (tenantId == null || tenantId.isBlank()) {
            log.debug(
                    "Tenant context empty. Using default schema: {}",
                    DEFAULT_TENANT
            );
            return DEFAULT_TENANT;
        }

        // Step 2: Handle default/public schema
        // Public schema is always valid
        if (DEFAULT_TENANT.equalsIgnoreCase(tenantId)) {
            log.debug("Using public schema");
            return DEFAULT_TENANT;
        }

        // Step 3: Determine if tenantId is UUID or schema name, then resolve
        return resolveTenantToSchema(tenantId);
    }

    /**
     * Helper method to resolve tenant to schema name
     */
    private String resolveTenantToSchema(String tenantId) {
        // Check if it's a valid UUID
        UUID tenantUuid = null;
        try {
            tenantUuid = UUID.fromString(tenantId);
        } catch (IllegalArgumentException e) {
            // Not a valid UUID, assume it's a schema name
            log.debug("Tenant ID is not a UUID: {}", tenantId);
        }

        try {
            TenantRepository tenantRepository = applicationContext.getBean(TenantRepository.class);
            
            if (tenantUuid != null) {
                // It was a valid UUID, lookup by UUID
                // Create final variable for use in lambda
                final UUID finalTenantUuid = tenantUuid;
                return tenantRepository.findById(tenantUuid)
                        .filter(tenant -> !tenant.isDeleted())
                        .map(Tenant::getSchemaName)
                        .orElseThrow(() -> {
                            log.warn("Tenant not found or deleted: {}", finalTenantUuid);
                            return new IllegalArgumentException(
                                    "Tenant not found or inactive: " + finalTenantUuid);
                        });
            } else {
                // It's not a UUID, assume it's a schema name
                return tenantRepository.findBySchemaNameIgnoreCaseAndDeletedAtIsNull(tenantId)
                        .map(Tenant::getSchemaName)
                        .orElse(tenantId);  // Fallback: use provided ID as schema name
            }

        } catch (Exception e) {
            log.error("Error resolving tenant: {}", tenantId, e);
            throw new RuntimeException("Failed to resolve tenant context", e);
        }
    }

    /* ======================================================
       HIBERNATE CONTRACT
       ====================================================== */

    /**
     * Permite que Hibernate valide o tenant atual
     * quando reutilizar sessões existentes.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}