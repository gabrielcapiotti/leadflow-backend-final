package com.leadflow.backend.controller.billing;

import com.leadflow.backend.dto.billing.WebhookAlertDTO;
import com.leadflow.backend.entities.WebhookAlertEvent;
import com.leadflow.backend.service.billing.WebhookAlertService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing/webhooks/alerts")
@RequiredArgsConstructor
@Tag(name = "Webhook Alerts", description = "Webhook monitoring and alert management endpoints")
public class WebhookAlertController {

    private final WebhookAlertService alertService;

    // ================= GET =================

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebhookAlertDTO>> getAllActiveAlerts() {
        return ResponseEntity.ok(
                alertService.toDTO(alertService.getActiveAlerts())
        );
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebhookAlertDTO>> getTenantAlerts(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(
                alertService.toDTO(alertService.getActiveAlertsByTenant(tenantId))
        );
    }

    @GetMapping("/critical")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebhookAlertDTO>> getCriticalAlerts() {
        return ResponseEntity.ok(
                alertService.toDTO(alertService.getCriticalAlerts())
        );
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<WebhookAlertDTO>> getAlertHistory(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "24") int hoursBack,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            if (tenantId == null) {
                return ResponseEntity.noContent().build();
            }
            if (hoursBack <= 0) {
                return ResponseEntity.badRequest().build();
            }

            int validatedSize = Math.min(Math.max(size, 1), 100);
            int validatedPage = Math.max(page, 0);

            Pageable pageable = PageRequest.of(validatedPage, validatedSize);

            Page<WebhookAlertEvent> history = alertService.getAlertHistory(
                    tenantId,
                    hoursBack,
                    pageable
            );
            
            if (history == null || history.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(alertService.toDTO(history));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching alert history", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookAlertDTO.AlertStatsDTO> getAlertStats() {
        return ResponseEntity.ok(alertService.getAlertStats());
    }

    @GetMapping("/by-type/{alertType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebhookAlertDTO>> getAlertsByType(@PathVariable String alertType) {
        try {
            WebhookAlertEvent.AlertType type = alertService.parseAlertType(alertType);
            return ResponseEntity.ok(
                    alertService.toDTO(alertService.getAlertsByType(type))
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching alerts by type", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/by-severity/{severity}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebhookAlertDTO>> getAlertsBySeverity(@PathVariable String severity) {
        try {
            WebhookAlertEvent.AlertSeverity sev = alertService.parseSeverity(severity);
            return ResponseEntity.ok(
                    alertService.toDTO(alertService.getAlertsBySeverity(sev))
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching alerts by severity", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ================= POST =================

    @PostMapping("/{alertId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookAlertDTO> resolveAlert(@PathVariable UUID alertId) {

        WebhookAlertEvent resolved = alertService.resolveAlert(alertId);

        if (resolved == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(WebhookAlertDTO.fromEntity(resolved));
    }

    @PostMapping("/resolve-by-type/{alertType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resolveAlertsByType(
            @RequestParam(required = false) UUID tenantId,
            @PathVariable String alertType) {

        try {
            if (tenantId == null) {
                return ResponseEntity.ok(Map.of(
                        "message", "No tenant specified - alerts resolved by type only",
                        "count", 0,
                        "alertType", alertType,
                        "note", "Provide tenantId parameter to resolve specific tenant alerts"
                ));
            }
            if (alertType == null || alertType.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Alert type is required"));
            }
            
            WebhookAlertEvent.AlertType type = alertService.parseAlertType(alertType);
            int count = alertService.resolveAlertsByType(tenantId, type);

            return ResponseEntity.ok(Map.of(
                    "message", "Alerts resolved successfully",
                    "count", count,
                    "alertType", alertType,
                    "tenantId", tenantId
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid alert type: " + alertType));
        } catch (Exception e) {
            log.error("Error resolving alerts by type", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal server error"));
        }
    }
}
