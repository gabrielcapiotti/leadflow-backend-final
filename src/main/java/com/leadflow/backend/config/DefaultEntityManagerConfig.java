package com.leadflow.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Configuration
public class DefaultEntityManagerConfig {

    private final DataSource dataSource;

    public DefaultEntityManagerConfig(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    /* ======================================================
       ENTITY MANAGER FACTORY
       
       NOTE: Multi-tenancy é implementado em APPLICATION LEVEL
       via TenantContext (ThreadLocal) + TenantFilter + Column-based filtering
       
       Não usamos Hibernate native multi-tenancy aqui porque:
       - Projeto usa UUID-based (COLUMN-based) isolation
       - Filtragem por tenant_id é feita explicitamente em queries
       - TenantContext fornece o UUID do tenant para o código
       
       ====================================================== */

    @Bean(name = "entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder
    ) {

        Map<String, Object> properties = new HashMap<>();

        // ✅ NO HIBERNATE MULTI-TENANCY - handled by application
        // Flyway manages schema creation/migration, Hibernate just validates entities
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.format_sql", false);
        properties.put("hibernate.use_sql_comments", false);
        properties.put("hibernate.jdbc.time_zone", "UTC");

        return builder
                .dataSource(dataSource)
                .packages(
                        "com.leadflow.backend.entities",
                        "com.leadflow.backend.webhook.entity",
                        "com.leadflow.domain.auth"
                )
                .persistenceUnit("default")
                .properties(properties)
                .build();
    }

    /* ======================================================
       TRANSACTION MANAGER
       ====================================================== */

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory")
            EntityManagerFactory entityManagerFactory
    ) {

        Objects.requireNonNull(entityManagerFactory);

        return new JpaTransactionManager(entityManagerFactory);
    }

    /* ======================================================
       CLOCK
       ====================================================== */

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}