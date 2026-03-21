package com.leadflow.backend.multitenancy.filter;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Configuração central para Hibernate Filters multi-tenant.
 * 
 * Responsável por ativar filtros dinamicamente baseado no
 * tenant definido no TenantContext.
 * 
 * Chamado automaticamente pela infraestrutura antes de queries.
 */
@Component
public class HibernateFilterConfiguration {

    public static final String TENANT_FILTER = "tenantFilter";
    public static final String TENANT_FILTER_PARAM = "tenantId";

    /**
     * Ativa Hibernate Filter para o tenant atual.
     * 
     * Deve ser chamado:
     * - Em JpaRepository customizado
     * - Em Query customizado
     * - Em Specification
     * 
     * ✅ Uso seguro: enforce automático de isolamento
     */
    public static void enableTenantFilter(EntityManager em) {
        
        String tenant = TenantContext.getTenant();
        
        Session session = em.unwrap(Session.class);
        
        session
            .enableFilter(TENANT_FILTER)
            .setParameter(TENANT_FILTER_PARAM, tenant);
    }

    /**
     * Desativa Hibernate Filter (usar com CUIDADO).
     * 
     * Apenas para operações genuinamente cross-tenant:
     * - Admin reports
     * - System cleanup
     * - Data migration
     */
    public static void disableTenantFilter(EntityManager em) {
        
        Session session = em.unwrap(Session.class);
        
        if (session.getEnabledFilter(TENANT_FILTER) != null) {
            session.disableFilter(TENANT_FILTER);
        }
    }

    /**
     * Verifica se filtro está ativo.
     */
    public static boolean isTenantFilterEnabled(EntityManager em) {
        
        Session session = em.unwrap(Session.class);
        
        return session.getEnabledFilter(TENANT_FILTER) != null;
    }
}
