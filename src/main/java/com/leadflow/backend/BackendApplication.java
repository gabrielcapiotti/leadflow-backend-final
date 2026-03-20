package com.leadflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@EnableScheduling
@EnableRetry
@EnableCaching
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {"com.leadflow.backend", "com.leadflow.domain"})
@EnableJpaRepositories(basePackages = {
    "com.leadflow.backend.repository",
    "com.leadflow.backend.webhook.repository",
    "com.leadflow.domain.auth.repository"
})
@EntityScan(basePackages = {
    "com.leadflow.backend.entities",
    "com.leadflow.backend.webhook.entity",
    "com.leadflow.domain"
})

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}