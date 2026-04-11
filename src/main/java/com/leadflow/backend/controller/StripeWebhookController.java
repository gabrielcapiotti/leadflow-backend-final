package com.leadflow.backend.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.StripeCustomerRepository;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.service.billing.*;
import com.leadflow.backend.util.StripeEventJsonExtractor;
import com.leadflow.backend.webhook.resolver.WebhookTenantResolver;
import com.leadflow.backend.webhook.service.WebhookReplayService;
import com.stripe.model.Event;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeService stripeService;
    private final StripeWebhookProcessor webhookProcessor;
    private final StripeEventIdempotencyService idempotencyService;
    private final WebhookLoggingService webhookLoggingService;
    private final WebhookMetricsTracker metricsTracker;
    private final WebhookReplayService webhookReplayService;
    private final WebhookTenantResolver webhookTenantResolver;
    private final StripeEventLogRepository eventLogRepository;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) throws IOException {
        long startTime = System.currentTimeMillis();
        String eventId = null;
        String eventType = null;
        String customerId = null;
        UUID tenantUuid = null;
        String payload = null;

        try {
            // ========================================================================
            // STEP 1: Read RAW request body (never transform before validation)
            // ========================================================================
            payload = new String(
                request.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
            );
            String signatureHeader = request.getHeader("Stripe-Signature");

            log.info("[WEBHOOK] 📥 Received webhook - payload size: {} bytes", payload.length());

            // ========================================================================
            // STEP 2: VALIDATE STRIPE SIGNATURE (BEFORE any persistence/processing)
            // ========================================================================
            Event event = null;
            try {
                event = stripeService.constructWebhookEvent(payload, signatureHeader);
                log.info("[WEBHOOK] ✅ Stripe signature validated");
            } catch (Exception validationEx) {
                log.error("[WEBHOOK] ❌ Stripe validation FAILED (rejecting webhook): {}", validationEx.getMessage());
                // 🔥 CRITICAL: Return 200 to prevent retries, but DO NOT persist
                return ResponseEntity.ok("{}");
            }

            if (event == null) {
                log.error("[WEBHOOK] Event is null after validation");
                return ResponseEntity.ok("{}");
            }

            // ========================================================================
            // STEP 3: Extract event metadata (before tenant resolution)
            // ========================================================================
            eventId = event.getId();
            eventType = event.getType();

            log.info("[WEBHOOK] 📋 Event parsed: id={}, type={}", eventId, eventType);

            // ========================================================================
            // STEP 4: Extract tenant context from webhook payload
            // ========================================================================
            JsonObject dataObject = StripeEventJsonExtractor.extractDataObjectAsJson(event);
            customerId = StripeEventJsonExtractor.getString(dataObject, "customer");
            if (customerId == null || customerId.isBlank()) {
                customerId = "unknown";
            }

            // Extract metadata to get tenantId (PRIMARY source for tenant resolution)
            String metadataTenantId = extractTenantIdFromMetadata(payload);

            log.info("[WEBHOOK] 🔍 Extracted: customerId={}, metadataTenantId={}", customerId, metadataTenantId);

            // ========================================================================
            // STEP 5: Resolve tenant using WebhookTenantResolver (priority order)
            // ========================================================================
            Optional<UUID> resolvedTenant = webhookTenantResolver.resolveTenant(metadataTenantId, customerId);

            if (resolvedTenant.isEmpty()) {
                log.warn("[WEBHOOK] ⚠️  Cannot resolve tenant - storing in global fallback");
                // Event will be stored in global fallback (not tenant-scoped)
                webhookReplayService.storeFailedWebhook(
                    eventId,
                    eventType,
                    payload,
                    "PENDING_TENANT_RESOLUTION"
                );
                return ResponseEntity.ok("{}");
            }

            tenantUuid = resolvedTenant.get();
            log.info("[WEBHOOK] 👤 Tenant resolved: {}", tenantUuid);

            // ========================================================================
            // STEP 6: Check idempotency (before processing)
            // ========================================================================
            if (idempotencyService.isDuplicate(eventId)) {
                log.info("[WEBHOOK] ℹ️  Duplicate event detected (ignoring): {}", eventId);
                return ResponseEntity.ok("{}");
            }

            // ========================================================================
            // STEP 7: Set TenantContext (ONLY with valid tenant)
            // ========================================================================
            TenantContext.setTenant(tenantUuid);
            log.info("[WEBHOOK] 🔐 TenantContext set: {}", tenantUuid);

            try {
                // ====================================================================
                // STEP 8: Persist event (ONLY after validation + tenant resolution)
                // ====================================================================
                idempotencyService.saveEvent(eventId, eventType, payload, tenantUuid, customerId);
                log.info("[WEBHOOK] 💾 Event persisted: {}", eventId);

                // Record metric: event received
                metricsTracker.recordEventReceived(tenantUuid, eventType, eventId);

                // ====================================================================
                // STEP 9: Process event (within tenant context)
                // ====================================================================
                long processingStartTime = System.currentTimeMillis();

                try {
                    webhookProcessor.process(event);
                    idempotencyService.markProcessed(eventId);

                    long processingDuration = System.currentTimeMillis() - processingStartTime;
                    metricsTracker.recordEventProcessed(tenantUuid, eventType, processingDuration);
                    webhookLoggingService.logWebhookProcessed(
                        event, false, LocalDateTime.now(), processingDuration
                    );

                    log.info("[WEBHOOK] ✅ Event processed successfully: {}", eventId);

                } catch (Exception processingEx) {
                    long processingDuration = System.currentTimeMillis() - processingStartTime;
                    
                    idempotencyService.markFailed(eventId, processingEx.getMessage());
                    metricsTracker.recordEventFailed(tenantUuid, eventType, "processing_error", processingDuration);
                    webhookLoggingService.logWebhookFailed(
                        eventId, eventType, processingEx.getMessage(), processingDuration
                    );

                    log.error("[WEBHOOK] ❌ Event processing failed: {}", eventId, processingEx);
                }

            } catch (Exception e) {
                log.error("[WEBHOOK] 💥 Error during event persistence/processing: {}", e.getMessage(), e);
                idempotencyService.markFailed(eventId, e.getMessage());
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("[WEBHOOK] ⏱️  Webhook processing completed in {}ms", totalDuration);
            return ResponseEntity.ok("{}");

        } catch (Exception e) {
            log.error("[WEBHOOK] 🔥 CRITICAL ERROR: {}", e.getMessage(), e);
            return ResponseEntity.ok("{}");

        } finally {
            // ========================================================================
            // STEP 10: Always clear TenantContext (thread safety)
            // ========================================================================
            TenantContext.clear();
            log.debug("[WEBHOOK] 🧹 TenantContext cleared");
        }
    }

    /**
     * Extract event ID from raw JSON payload for early identification.
     * Used only for logging/identification, not for processing.
     */
    private String extractEventIdFromPayload(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            if (json.has("id")) {
                return json.get("id").getAsString();
            }
        } catch (Exception e) {
            log.debug("[WEBHOOK] Failed to extract event ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract event type from raw JSON payload for logging.
     */
    private String extractEventTypeFromPayload(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            if (json.has("type")) {
                return json.get("type").getAsString();
            }
        } catch (Exception e) {
            log.debug("[WEBHOOK] Failed to extract event type: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Extract tenantId from event metadata (stored in data.object.metadata.tenantId).
     * This is the PRIMARY source for tenant resolution.
     * 
     * @param payload Raw JSON payload
     * @return tenantId from metadata, or null if not found
     */
    private String extractTenantIdFromMetadata(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            
            // Navigate: { data: { object: { metadata: { tenantId: "..." } } } }
            if (json.has("data")) {
                JsonObject dataObj = json.getAsJsonObject("data");
                if (dataObj.has("object")) {
                    JsonObject objectData = dataObj.getAsJsonObject("object");
                    if (objectData.has("metadata")) {
                        JsonObject metadata = objectData.getAsJsonObject("metadata");
                        if (metadata.has("tenantId")) {
                            String tenantId = metadata.get("tenantId").getAsString();
                            log.debug("[WEBHOOK] Extracted metadata.tenantId: {}", tenantId);
                            return tenantId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[WEBHOOK] Failed to extract tenantId from metadata: {}", e.getMessage());
        }
        return null;
    }
}