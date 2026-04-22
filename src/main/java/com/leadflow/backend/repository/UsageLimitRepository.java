package com.leadflow.backend.repository;

import com.leadflow.backend.entities.UsageLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsageLimitRepository extends JpaRepository<UsageLimit, UUID> {

    /**
     * Finds usage limit by tenantId without lock (for dashboard reads).
     * Use this for read-only operations like dashboards and reports.
     */
    Optional<UsageLimit> findByTenantId(UUID tenantId);

    /**
     * Finds usage limit by tenantId with pessimistic write lock
     * to prevent race conditions during concurrent updates.
     * Use this for write operations that need concurrency control.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UsageLimit u WHERE u.tenantId = :tenantId")
    Optional<UsageLimit> findByTenantIdForUpdate(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);

}
