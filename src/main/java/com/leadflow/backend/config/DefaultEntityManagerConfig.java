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
       ====================================================== */

    @Bean(name = "entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder
    ) {

        Map<String, Object> properties = new HashMap<>();

        // 🔥 SEM MULTI-TENANCY POR SCHEMA
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

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