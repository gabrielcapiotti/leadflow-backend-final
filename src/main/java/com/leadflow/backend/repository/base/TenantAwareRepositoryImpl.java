package com.leadflow.backend.repository.base;

import com.leadflow.backend.multitenancy.filter.HibernateFilterConfiguration;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

/**
 * Base para todos os repositories.
 * 
 * ✅ Ativa Hibernate Filter automaticamente em find operations
 * ✅ Garante isolamento tenant sem precisar de query customizado
 * ✅ Transparente para a aplicação
 */
public class TenantAwareRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private final EntityManager em;

    public TenantAwareRepositoryImpl(Class<T> domainClass, EntityManager em) {
        super(domainClass, em);
        this.em = em;
    }

    @Override
    public void flush() {
        HibernateFilterConfiguration.enableTenantFilter(em);
        super.flush();
    }
}
