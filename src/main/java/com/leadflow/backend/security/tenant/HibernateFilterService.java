package com.leadflow.backend.security.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.Session;
import org.hibernate.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 🔒 MULTI-TENANT SECURITY: Hibernate Filter Service
 *
 * Garante que o filtro tenantFilter seja aplicado corretamente
 * na MESMA sessão Hibernate da transação atual.
 *
 * ⚠️ IMPORTANTE:
 * - Usa EntityManager transacional (@PersistenceContext)
 * - Evita inconsistência entre sessões
 */
@Service
public class HibernateFilterService {

    private static final Logger log =
            LoggerFactory.getLogger(HibernateFilterService.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * ✅ Ativa o filtro multi-tenant na sessão atual
     */
    public void enableTenantFilter(String tenantId) {

        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or empty");
        }

        try {
            Session session = entityManager.unwrap(Session.class);

            Filter existingFilter = session.getEnabledFilter("tenantFilter");

            if (existingFilter != null) {
                // Evita sobrescrever se já estiver ativo
                return;
            }

            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);

            log.debug("Tenant filter ENABLED for tenant={}", tenantId);

        } catch (Exception e) {
            log.error("Failed to enable tenant filter", e);
            throw new RuntimeException("Failed to enable tenant filter", e);
        }
    }

    /**
     * ✅ Desativa o filtro após a request
     */
    public void disableTenantFilter() {

        try {
            Session session = entityManager.unwrap(Session.class);

            if (session.getEnabledFilter("tenantFilter") != null) {
                session.disableFilter("tenantFilter");
                log.debug("Tenant filter DISABLED");
            }

        } catch (Exception e) {
            log.warn("Failed to disable tenant filter: {}", e.getMessage());
        }
    }

    /**
     * 🔍 Verifica se o filtro está ativo (debug / auditoria)
     */
    public boolean isTenantFilterEnabled() {

        try {
            Session session = entityManager.unwrap(Session.class);
            return session.getEnabledFilter("tenantFilter") != null;

        } catch (Exception e) {
            return false;
        }
    }
}