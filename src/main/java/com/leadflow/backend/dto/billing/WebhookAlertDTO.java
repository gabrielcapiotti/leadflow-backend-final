package com.leadflow.backend.dto.billing;

import com.leadflow.backend.entities.WebhookAlertEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * WebhookAlertDTO - API response model for webhook alerts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookAlertDTO {

    private UUID alertId;
    private String alertType;              // CIRCUIT_BREAKER_OPENED, HIGH_FAILURE_RATE, etc.
    private String severity;               // CRITICAL, WARNING, INFO
    private UUID tenantId;
    private String message;
    private Map<String, Object> metrics;   // Context data (failure count, retry count, etc.)
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private Long durationMinutes;          // Age of alert if still active
    private Boolean isActive;

    /**
     * Convert from entity to DTO
     */
    public static WebhookAlertDTO fromEntity(WebhookAlertEvent entity) {
        long durationMin = entity.getAgeInMinutes();

        return WebhookAlertDTO.builder()
                .alertId(entity.getId())
                .alertType(entity.getAlertType().name())
                .severity(entity.getSeverity().name())
                .tenantId(entity.getTenantId())
                .message(entity.getMessage())
                .metrics(entity.getMetrics())
                .createdAt(entity.getCreatedAt())
                .resolvedAt(entity.getResolvedAt())
                .durationMinutes(entity.isActive() ? durationMin : null)
                .isActive(entity.isActive())
                .build();
    }

    /**
     * Summary for list views
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AlertSummaryDTO {
        private UUID alertId;
        private String alertType;
        private String severity;
        private UUID tenantId;
        private String message;
        private LocalDateTime createdAt;
        private Long durationMinutes;
        private Boolean isActive;

        public static AlertSummaryDTO fromEntity(WebhookAlertEvent entity) {
            return AlertSummaryDTO.builder()
                    .alertId(entity.getId())
                    .alertType(entity.getAlertType().name())
                    .severity(entity.getSeverity().name())
                    .tenantId(entity.getTenantId())
                    .message(entity.getMessage())
                    .createdAt(entity.getCreatedAt())
                    .durationMinutes(entity.isActive() ? entity.getAgeInMinutes() : null)
                    .isActive(entity.isActive())
                    .build();
        }
    }

    /**
     * Statistics for dashboard
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AlertStatsDTO {
        private Long totalActive;           // Total unresolved alerts
        private Long criticalCount;         // Count of CRITICAL severity
        private Long warningCount;          // Count of WARNING severity
        private Long infoCount;             // Count of INFO severity
        private Long resolvedInLastHour;    // How many were resolved in last 60 min
        private Map<String, Long> alertTypeDistribution;  // Count by alert type
        private LocalDateTime lastAlertAt;
    }
}
