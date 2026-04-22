package com.leadflow.backend.repository.tenant;

import com.leadflow.backend.entities.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /* ======================================================
       ACTIVE TENANTS (Soft Delete Applied) - UUID BASED
       ====================================================== */

    /**
     * Find active tenant by name (case-insensitive).
     */
    Optional<Tenant> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

    /**
     * Find active tenant by ID.
     */
    Optional<Tenant> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Check if active tenant exists by name.
     */
    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);
}