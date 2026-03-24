package com.leadflow.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * WebhookAlertEvent - Represents system alerts triggered by webhook processing issues
 * Used to notify admins about critical events (circuit breaker opens, high failure rates, etc.)
 */
@Entity
@Table(name = "webhook_alerts", indexes = {
        @Index(name = "idx_webhook_alerts_tenant_created", columnList = "tenant_id, created_at DESC"),
        @Index(name = "idx_webhook_alerts_severity_created", columnList = "severity, created_at DESC"),
        @Index(name = "idx_webhook_alerts_active", columnList = "resolved_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookAlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metrics;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if alert is currently active (not resolved)
     */
    public boolean isActive() {
        return resolvedAt == null;
    }

    /**
     * Get age of alert in minutes
     */
    public long getAgeInMinutes() {
        return java.time.temporal.ChronoUnit.MINUTES.between(createdAt, LocalDateTime.now());
    }

    /**
     * Resolve this alert
     */
    public void resolve() {
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * Alert Type Enum - Categorizes alert triggers
     */
    public enum AlertType {
        CIRCUIT_BREAKER_OPENED("Circuit breaker aberto - webhooks rejeitados temporariamente"),
        HIGH_FAILURE_RATE("Taxa de falha muito alta - > 50% nos últimos 5 min"),
        PROCESSING_STALLED("Processamento travou - nenhum webhook processado nos últimos 10 min"),
        EXCESSIVE_RETRIES("Muitas tentativas de retry - webhook com 5+ retries"),
        TIMEOUT_DETECTED("Timeout detectado - latência média > 5 segundos"),
        DATABASE_ERROR("Erro no banco de dados - falha ao salvar webhook event");

        private final String description;

        AlertType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Alert Severity Enum - Determines urgency level
     */
    public enum AlertSeverity {
        CRITICAL("Crítico - Requer ação imediata"),
        WARNING("Aviso - Situação anormal detectada"),
        INFO("Informativo - Evento de monitoramento");

        private final String description;

        AlertSeverity(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
