package com.leadflow.backend.security.tenant;

import org.hibernate.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;

/**
 * 🔒 MULTI-TENANT SECURITY: Hibernate Filter Service
 * 
 * Ativa AUTOMATICAMENTE o filtro tenantFilter em toda sessão Hibernate.
 * Isso garante que NENHUMA query dirá dados de outro tenant,
 * mesmo que o desenvolvedor esqueça de adicionar WHERE tenant_id = ?
 * 
 * Padrão: Chain of responsibility + decorator no filters Spring
 */
@Service
public class HibernateFilterService {

    private final ObjectProvider<EntityManager> entityManagerProvider;

    public HibernateFilterService(ObjectProvider<EntityManager> entityManagerProvider) {
        this.entityManagerProvider = entityManagerProvider;
    }

    /**
     * ✅ Ativa o filtro multi-tenant para a sessão atual
     * 
     * 🔥 OBRIGATÓRIO ser chamado em @Component TenantFilter
     * 
     * @param tenantId tenant a ser isolado
     * @throws IllegalArgumentException se tenantId for null/vazio
     */
    public void enableTenantFilter(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or empty");
        }

        try {
            EntityManager entityManager = entityManagerProvider.getObject();
            Session session = entityManager.unwrap(Session.class);
            
            // Ativa o filtro 'tenantFilter' definido em @FilterDef
            session.enableFilter("tenantFilter")
                    .setParameter("tenantId", tenantId);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to enable tenant filter", e);
        }
    }

    /**
     * 🗑️ Desativa o filtro (limpeza)
     * 
     * Chamado em finally block do TenantFilter HTTP
     */
    public void disableTenantFilter() {
        try {
            EntityManager entityManager = entityManagerProvider.getObject();
            Session session = entityManager.unwrap(Session.class);
            session.disableFilter("tenantFilter");
        } catch (Exception e) {
            // Log mas não explode - pode ser já desativado
            System.err.println("Failed to disable tenant filter: " + e.getMessage());
        }
    }

    /**
     * ✅ Valida isolamento ativo
     * 
     * Debug/Audit: confirma que o filtro está ativo
     * 
     * @return true se o filtro está ativo
     */
    public boolean isTenantFilterEnabled() {
        try {
            EntityManager entityManager = entityManagerProvider.getObject();
            Session session = entityManager.unwrap(Session.class);
            return session.getEnabledFilter("tenantFilter") != null;
        } catch (Exception e) {
            return false;
        }
    }
}
