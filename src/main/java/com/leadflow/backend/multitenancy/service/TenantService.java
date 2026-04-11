package com.leadflow.backend.multitenancy.service;

import com.leadflow.backend.exception.TenantNotFoundException;
import com.leadflow.backend.entities.Tenant;
import com.leadflow.backend.repository.tenant.TenantRepository;
import org.flywaydb.core.Flyway;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TenantService {

    private static final String DEFAULT_SCHEMA = "public";

    private static final Pattern VALID_SCHEMA =
            Pattern.compile("^[a-z0-9_]{3,50}$");

    private static final String RESOLVE_SCHEMA_SQL = """
            SELECT schema_name
            FROM public.tenants
            WHERE schema_name = ?
              AND deleted_at IS NULL
            """;

    private static final String RESOLVE_ID_SQL = """
            SELECT id
            FROM public.tenants
            WHERE schema_name = ?
              AND deleted_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final TenantRepository tenantRepository;

    private static final Logger logger =
            LoggerFactory.getLogger(TenantService.class);

    public TenantService(JdbcTemplate jdbcTemplate, DataSource dataSource, TenantRepository tenantRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.tenantRepository = tenantRepository;
    }

    /* ======================================================
       RESOLVE TENANT → SCHEMA
       ====================================================== */

    public Optional<String> resolveSchemaByTenantIdentifier(String identifier) {

        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(identifier);

        if (!VALID_SCHEMA.matcher(normalized).matches()) {
            return Optional.empty();
        }

        try {
            String schema = jdbcTemplate.queryForObject(
                    RESOLVE_SCHEMA_SQL,
                    String.class,
                    normalized
            );

            return Optional.ofNullable(schema).map(this::normalize);

        } catch (EmptyResultDataAccessException ex) {
            logger.warn("Tenant not found for identifier: {}", identifier);
            return Optional.empty();
        }
    }

    /* ======================================================
       RESOLVE TENANT → ID (CRÍTICO)
       ====================================================== */

    public UUID getTenantIdBySchema(String schema) {

        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Schema cannot be blank");
        }

        String normalized = normalize(schema);

        if (!VALID_SCHEMA.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid schema format");
        }

        try {
            return jdbcTemplate.queryForObject(
                    RESOLVE_ID_SQL,
                    UUID.class,
                    normalized
            );

        } catch (EmptyResultDataAccessException ex) {
            logger.error("Tenant not found for schema: {}", schema);
            throw new TenantNotFoundException(
                    "Tenant not found for schema: " + schema
            );
        }
    }

    /* ======================================================
       CREATE TENANT RECORD (FOR LOGIN AUDIT RESOLUTION)
       ====================================================== */

    public Tenant createTenant(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be blank");
        }

        String normalized = normalize(schemaName);

        if (!VALID_SCHEMA.matcher(normalized).matches()) {
            logger.error("❌ Schema validation failed: {} (normalized: {})", schemaName, normalized);
            throw new IllegalArgumentException("Invalid schema format: " + schemaName);
        }

        // Check if tenant already exists
        if (tenantRepository.existsBySchemaNameIgnoreCaseAndDeletedAtIsNull(normalized)) {
            logger.info("Tenant already exists: {}", normalized);
            // Return existing tenant
            return tenantRepository.findBySchemaNameIgnoreCaseAndDeletedAtIsNull(normalized)
                    .orElseThrow(() -> new IllegalStateException("Tenant should exist but not found"));
        }

        // Create Tenant record - let Hibernate generate the UUID
        Tenant tenant = new Tenant(normalized, normalized);
        
        Tenant savedTenant = tenantRepository.saveAndFlush(tenant);
        logger.info("✅ Tenant record created in database: id={}, schema={}", savedTenant.getId(), normalized);
        
        return savedTenant;
    }

    /* ======================================================
       INITIALIZE TENANT SCHEMA
       ====================================================== */

    public void initializeTenantSchema(String schema) {

        validateSchemaName(schema);

        String normalized = normalize(schema);

        logger.info("Initializing schema for tenant: {}", normalized);

        // cria o schema
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + normalized + "\"");

        // executa migrations no schema do tenant
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(normalized)
                .defaultSchema(normalized)
                .locations("classpath:db/migration/tenant")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load()
                .migrate();

        logger.info("Tenant schema initialized successfully: {}", normalized);
    }

    /* ======================================================
       VALIDATE SCHEMA NAME
       ====================================================== */

    public void validateSchemaName(String schema) {

        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Schema cannot be blank");
        }

        String normalized = normalize(schema);

        if (!VALID_SCHEMA.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid schema format");
        }
    }

    /* ======================================================
       DEFAULT FALLBACK
       ====================================================== */

    public String getDefaultSchema() {
        return DEFAULT_SCHEMA;
    }

    /* ======================================================
       INTERNAL UTIL
       ====================================================== */

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}