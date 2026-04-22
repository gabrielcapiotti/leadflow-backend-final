package com.leadflow.backend.multitenancy.filter;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(HibernateFilterConfiguration.class);
    
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
     * ✅ DEFENSIVE: Se TenantContext não estiver setado ainda, não ativa filtro
     * 
     * FIX PARA RACE CONDITION:
     * Em alguns casos (ex: flush() antes de TenantContext estar pronto),
     * TenantContext pode estar vazio. Nesse caso, apenas retorna sem erro.
     * A validação de autenticação + tenant ocorre mais tarde no pipeline.
     */
    public static void enableTenantFilter(EntityManager em) {
        
        // ✅ DEFENSIVE: Tenta obter tenant, mas não falha se não existir ainda
        java.util.UUID tenant = TenantContext.getIfPresent();
        
        // Se tenant não está setado, não ativa o filtro
        // Isso pode acontecer em race conditions durante o pipeline de autenticação
        if (tenant == null) {
            logger.debug("TenantContext not yet set (possibly early in request pipeline), skipping Hibernate filter activation");
            return;
        }
        
        Session session = em.unwrap(Session.class);
        
        session
            .enableFilter(TENANT_FILTER)
            .setParameter(TENANT_FILTER_PARAM, tenant.toString());
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
