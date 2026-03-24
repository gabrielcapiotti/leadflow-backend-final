package com.leadflow.backend.repository;

import com.leadflow.backend.entities.StripeEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripeEventLogRepository extends JpaRepository<StripeEventLog, Long> {

    Optional<StripeEventLog> findByEventId(String eventId);

    @Query("SELECT s FROM StripeEventLog s WHERE s.status = :status " +
           "AND s.nextRetryAt IS NOT NULL AND s.nextRetryAt <= CURRENT_TIMESTAMP " +
           "ORDER BY s.nextRetryAt ASC LIMIT 10")
    List<StripeEventLog> findPendingRetries(@Param("status") StripeEventLog.EventProcessingStatus status);

    @Query("SELECT s FROM StripeEventLog s WHERE s.status IN :statuses " +
           "ORDER BY s.createdAt DESC")
    List<StripeEventLog> findByStatuses(@Param("statuses") List<StripeEventLog.EventProcessingStatus> statuses);

    @Query("SELECT COUNT(s) FROM StripeEventLog s WHERE s.status = :status")
    long countByStatus(@Param("status") StripeEventLog.EventProcessingStatus status);

    @Query("SELECT COUNT(s) FROM StripeEventLog s WHERE s.eventType = :eventType " +
           "AND s.status = :status AND s.createdAt >= :since")
    long countByEventTypeAndStatusSince(
        @Param("eventType") String eventType,
        @Param("status") StripeEventLog.EventProcessingStatus status,
        @Param("since") LocalDateTime since
    );

    // ========== TENANT ISOLATION QUERIES ==========

    @Query("SELECT s FROM StripeEventLog s WHERE s.tenantId = :tenantId " +
           "AND s.status = :status " +
           "AND s.nextRetryAt IS NOT NULL AND s.nextRetryAt <= CURRENT_TIMESTAMP " +
           "ORDER BY s.nextRetryAt ASC LIMIT 10")
    List<StripeEventLog> findPendingRetriesByTenant(
        @Param("tenantId") UUID tenantId,
        @Param("status") StripeEventLog.EventProcessingStatus status
    );

    @Query("SELECT s FROM StripeEventLog s WHERE s.tenantId = :tenantId " +
           "AND s.status IN :statuses ORDER BY s.createdAt DESC")
    List<StripeEventLog> findByTenantIdAndStatuses(
        @Param("tenantId") UUID tenantId,
        @Param("statuses") List<StripeEventLog.EventProcessingStatus> statuses
    );

    @Query("SELECT COUNT(s) FROM StripeEventLog s WHERE s.tenantId = :tenantId " +
           "AND s.status = :status")
    long countByTenantIdAndStatus(
        @Param("tenantId") UUID tenantId,
        @Param("status") StripeEventLog.EventProcessingStatus status
    );

    @Query("SELECT s FROM StripeEventLog s WHERE s.tenantId = :tenantId " +
           "AND s.customerId = :customerId ORDER BY s.createdAt DESC")
    List<StripeEventLog> findByTenantIdAndCustomerId(
        @Param("tenantId") UUID tenantId,
        @Param("customerId") String customerId
    );

    @Query("SELECT DISTINCT s.tenantId FROM StripeEventLog s WHERE s.status = :status " +
           "AND s.nextRetryAt IS NOT NULL AND s.nextRetryAt <= CURRENT_TIMESTAMP")
    List<UUID> findDistinctTenantsWithPendingRetries(
        @Param("status") StripeEventLog.EventProcessingStatus status
    );

    // ========== WEBHOOK ALERTS MONITORING ==========

    @Query("SELECT DISTINCT s.tenantId FROM StripeEventLog s WHERE s.createdAt >= :since")
    List<UUID> findDistinctTenantsWithRecentEvents(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(s) FROM StripeEventLog s WHERE s.tenantId = :tenantId AND s.createdAt >= :since")
    long countByTenantIdAndCreatedAtAfter(
        @Param("tenantId") UUID tenantId,
        @Param("since") LocalDateTime since
    );

    @Query("SELECT COUNT(s) FROM StripeEventLog s WHERE s.tenantId = :tenantId AND s.createdAt >= :since " +
           "AND s.status = 'FAILED'")
    long countFailedByTenantIdAndCreatedAtAfter(
        @Param("tenantId") UUID tenantId,
        @Param("since") LocalDateTime since
    );

    @Query("SELECT s FROM StripeEventLog s WHERE s.tenantId = :tenantId " +
           "AND s.processedAt IS NOT NULL ORDER BY s.processedAt DESC LIMIT 1")
    Optional<StripeEventLog> findLastProcessedByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT s FROM StripeEventLog s WHERE s.tenantId = :tenantId AND s.retryCount >= :threshold " +
           "AND s.createdAt >= :since ORDER BY s.retryCount DESC")
    List<StripeEventLog> findExcessiveRetryEvents(
        @Param("tenantId") UUID tenantId,
        @Param("threshold") int threshold,
        @Param("since") LocalDateTime since
    );

    @Query("SELECT AVG(CAST((EXTRACT(EPOCH FROM s.processedAt) - EXTRACT(EPOCH FROM s.createdAt)) * 1000 AS DOUBLE)) " +
           "FROM StripeEventLog s WHERE s.tenantId = :tenantId " +
           "AND s.processedAt IS NOT NULL AND s.createdAt >= :since")
    Double getAverageProcessingTimeMs(
        @Param("tenantId") UUID tenantId,
        @Param("since") LocalDateTime since
    );

    // ========== FAILURE ANALYSIS QUERIES ==========

    @Query("SELECT s FROM StripeEventLog s WHERE s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<StripeEventLog> findByCreatedAtAfter(@Param("since") LocalDateTime since);
}
