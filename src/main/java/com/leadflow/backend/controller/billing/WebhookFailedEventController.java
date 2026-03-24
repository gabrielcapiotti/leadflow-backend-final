package com.leadflow.backend.controller.billing;

import com.leadflow.backend.webhook.entity.FailedWebhookEvent;
import com.leadflow.backend.webhook.service.WebhookReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for managing failed webhook events (tenant-scoped).
 * Provides endpoints for replaying failed webhook events.
 *
 * @author LeadFlow Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/billing/webhooks/failed")
@Tag(name = "Webhook Failed Events", description = "Manage failed webhook events")
public class WebhookFailedEventController {

    private final WebhookReplayService webhookReplayService;

    @Autowired
    public WebhookFailedEventController(WebhookReplayService webhookReplayService) {
        this.webhookReplayService = webhookReplayService;
    }

    /**
     * Manually replay a failed webhook event.
     * Resets the webhook to pending status and schedules immediate retry.
     *
     * @param webhookId ID of the webhook to replay
     * @return Updated webhook event or 404 if not found
     */
    @PostMapping("/{webhookId}/replay")
    @PreAuthorize("permitAll()")
    @Operation(
        summary = "Manually replay a failed webhook",
        description = "Manually replay a failed webhook event. Resets the retry counter and schedules immediate retry."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Webhook scheduled for replay",
            content = @Content(schema = @Schema(implementation = FailedWebhookEvent.class))
        ),
        @ApiResponse(responseCode = "404", description = "Webhook not found"),
        @ApiResponse(responseCode = "500", description = "Error replaying webhook")
    })
    public ResponseEntity<FailedWebhookEvent> replayFailedWebhook(
            @Parameter(description = "ID of the webhook to replay", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String webhookId) {

        log.info("Manual replay requested for failed webhook: {}", webhookId);
        try {
            // Validate webhook ID format first (must be valid UUID)
            try {
                java.util.UUID.fromString(webhookId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid webhook ID format: {}", webhookId);
                return ResponseEntity.notFound().build();
            }
            
            // Try to replay the webhook
            FailedWebhookEvent webhookEvent = webhookReplayService.manualReplay(webhookId);
            log.info("Failed webhook {} scheduled for replay", webhookId);
            return ResponseEntity.ok(webhookEvent);
            
        } catch (IllegalArgumentException e) {
            // Webhook not found
            log.warn("Webhook not found or invalid: {}", webhookId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            // Any other error
            log.error("Error replaying failed webhook {}: {}", webhookId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
