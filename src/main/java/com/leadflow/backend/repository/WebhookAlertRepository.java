package com.leadflow.backend.repository;

import com.leadflow.backend.entities.WebhookAlertEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for WebhookAlertEvent persistence and querying
 */
@Repository
public interface WebhookAlertRepository extends JpaRepository<WebhookAlertEvent, UUID> {

    /**
     * Find active alerts (not resolved) for all tenants
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.resolvedAt IS NULL ORDER BY a.createdAt DESC")
    List<WebhookAlertEvent> findActiveAlerts();

    /**
     * Find active alerts for a specific tenant
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.tenantId = :tenantId AND a.resolvedAt IS NULL ORDER BY a.createdAt DESC")
    List<WebhookAlertEvent> findActivAlertsByTenant(@Param("tenantId") UUID tenantId);

    /**
     * Find alerts by tenant and severity
     */
    List<WebhookAlertEvent> findByTenantIdAndSeverity(
            UUID tenantId,
            WebhookAlertEvent.AlertSeverity severity
    );

    /**
     * Find recent alerts for a tenant (last N hours)
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.tenantId = :tenantId AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<WebhookAlertEvent> findRecentByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("since") LocalDateTime since
    );

    /**
     * Find alerts by type and unresolved status
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.alertType = :alertType AND a.resolvedAt IS NULL")
    List<WebhookAlertEvent> findUnresolvedByType(
            @Param("alertType") WebhookAlertEvent.AlertType alertType
    );

    /**
     * Count unresolved alerts by severity
     */
    @Query("SELECT COUNT(a) FROM WebhookAlertEvent a WHERE a.severity = :severity AND a.resolvedAt IS NULL")
    long countUnresolvedBySeverity(
            @Param("severity") WebhookAlertEvent.AlertSeverity severity
    );

    /**
     * Find recent unresolved alerts of same type for a tenant (de-duplication check)
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.tenantId = :tenantId " +
           "AND a.alertType = :alertType AND a.resolvedAt IS NULL " +
           "AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<WebhookAlertEvent> findRecentUnresolvedSameType(
            @Param("tenantId") UUID tenantId,
            @Param("alertType") WebhookAlertEvent.AlertType alertType,
            @Param("since") LocalDateTime since
    );

    /**
     * Count alerts created in last hour by severity
     */
    @Query("SELECT COUNT(a) FROM WebhookAlertEvent a WHERE a.severity = :severity " +
           "AND a.createdAt >= :since")
    long countBySeverityInPeriod(
            @Param("severity") WebhookAlertEvent.AlertSeverity severity,
            @Param("since") LocalDateTime since
    );

    /**
     * Find all alerts for a tenant in time range
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.tenantId = :tenantId " +
           "AND a.createdAt >= :startTime AND a.createdAt <= :endTime " +
           "ORDER BY a.createdAt DESC")
    List<WebhookAlertEvent> findByTenantAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find critical unresolved alerts
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.severity = 'CRITICAL' AND a.resolvedAt IS NULL ORDER BY a.createdAt DESC")
    List<WebhookAlertEvent> findCriticalUnresolved();

    /**
     * Find unresolved alerts by severity
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.severity = :severity AND a.resolvedAt IS NULL ORDER BY a.createdAt DESC")
    List<WebhookAlertEvent> findUnresolvedBySeverity(
            @Param("severity") WebhookAlertEvent.AlertSeverity severity
    );

    /**
     * Find alert history for a tenant (paginated)
     */
    @Query("SELECT a FROM WebhookAlertEvent a WHERE a.tenantId = :tenantId " +
           "AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    Page<WebhookAlertEvent> findHistoryByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );
}
