package com.leadflow.backend.controller;

import com.google.gson.JsonObject;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.StripeCustomerRepository;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.service.billing.*;
import com.leadflow.backend.util.StripeEventJsonExtractor;
import com.stripe.model.Event;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeService stripeService;
    private final StripeWebhookProcessor webhookProcessor;
    private final StripeEventLogRepository eventLogRepository;
    private final StripeEventIdempotencyService idempotencyService;
    private final WebhookLoggingService webhookLoggingService;
    private final WebhookMetricsTracker metricsTracker;
    private final StripeCustomerRepository stripeCustomerRepository;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) throws IOException {

        long startTime = System.currentTimeMillis();

        String payload = request.getReader()
                .lines()
                .collect(Collectors.joining());

        String signatureHeader = request.getHeader("Stripe-Signature");

        try {
            log.info("[WEBHOOK] Received Stripe webhook");

            // ============================================================
            // 1️⃣ VALIDAR ASSINATURA
            // ============================================================
            Event event = stripeService.constructWebhookEvent(payload, signatureHeader);
            String eventId = event.getId();
            String eventType = event.getType();

            log.debug("[WEBHOOK] Event validated: id={}, type={}", eventId, eventType);

            // ============================================================
            // 2️⃣ VERIFICAR IDEMPOTÊNCIA (prevenir duplicação)
            // ============================================================
            if (idempotencyService.isDuplicate(eventId)) {
                log.info("[WEBHOOK] Evento duplicado recebido (será ignorado): {}", eventId);
                return ResponseEntity.ok("{}"); // Stripe espera 200 mesmo para duplicados
            }

            // ============================================================
            // 3️⃣ EXTRAIR INFORMAÇÕES DO EVENTO (usando JSON RAW)
            // ============================================================
            // 🔥 Extract customerID from raw JSON (not SDK deserialization)
            JsonObject dataObject = StripeEventJsonExtractor.extractDataObjectAsJson(event);
            String customerId = StripeEventJsonExtractor.getString(dataObject, "customer");
            if (customerId == null || customerId.isBlank()) {
                customerId = "unknown";
            }
            
            // 🔥 Resolve tenant from database (not Stripe API)
            UUID tenantUuid = resolveTenantFromCustomer(customerId);
            String tenantId = tenantUuid != null ? tenantUuid.toString() : "unknown";

            log.info("[WEBHOOK] Processing event: id={}, type={}, tenant={}, customer={}", 
                    eventId, eventType, tenantId, customerId);

            // ============================================================
            // 4️⃣ SALVAR EVENTO NO BANCO (para rastreamento + idempotência)
            // ============================================================
            idempotencyService.saveEvent(eventId, eventType, payload, tenantUuid, customerId);

            // 🔥 SECURITY: Validar tenant UUID (REJECTING NULL)
            if (tenantUuid == null) {
                log.error("🔴 SECURITY ALERT: Webhook event {} received without tenantId - REJECTING", eventId);
                throw new IllegalStateException("Webhook event must have valid tenantId");
            }

            // Record metric: event received
            metricsTracker.recordEventReceived(tenantUuid, eventType, eventId);

            long duration = System.currentTimeMillis() - startTime;
            webhookLoggingService.logWebhookReceived(event, true, customerId, duration);

            // ============================================================
            // 5️⃣ PROCESSAR EVENTO (com isolamento de tenant)
            // ============================================================
            try {
                // 🔥 TenantContext.setTenant() expects UUID (not String)
                // Only set if we have a valid tenant UUID
                if (tenantUuid != null) {
                    TenantContext.setTenant(tenantUuid);  // Pass UUID directly
                } else {
                    // For public webhooks without tenant context, skip setTenant
                    // (TenantContext will not be set, operations will fail if they require tenant)
                    log.debug("No tenant context available for webhook processing");
                }

                webhookProcessor.process(event);

                // ✅ Marcar como processado com sucesso
                idempotencyService.markProcessed(eventId);

                // Record metric: event processed successfully
                metricsTracker.recordEventProcessed(tenantUuid, eventType, duration);

                webhookLoggingService.logWebhookProcessed(
                        event, false, LocalDateTime.now(), duration
                );

                log.info("[WEBHOOK] Event processed successfully: {}", eventId);

            } catch (Exception e) {
                // ❌ Marcar para retry
                idempotencyService.markFailed(eventId, e.getMessage());

                // Record metric: event failed
                metricsTracker.recordEventFailed(tenantUuid, eventType, "processing_error", duration);

                webhookLoggingService.logWebhookFailed(eventId, eventType, e.getMessage(), duration);

                log.error("[WEBHOOK] Error processing event {}: {}", eventId, e.getMessage(), e);

            } finally {
                TenantContext.clear();
            }

            return ResponseEntity.ok("{}");

        } catch (Exception e) {
            // 🔥 Signature validation happens in constructWebhookEvent()
            // If we reach here, return 401 only for signature errors, 200 for internal errors
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            
            if (errorMsg.toLowerCase().contains("signature") || 
                errorMsg.toLowerCase().contains("invalid")) {
                log.warn("[WEBHOOK] Signature validation failed: {}", errorMsg);
                return ResponseEntity.status(401).body("Invalid signature");
            }
            
            // Internal error = return 200 to prevent Stripe retries
            log.error("[WEBHOOK] Internal error processing webhook: {}", errorMsg, e);
            return ResponseEntity.ok("{}");
        }
    }

    /**
     * Resolve tenant from customer mapping in database.
     * Returns null if customer not mapped (not an error - will retry later).
     * 
     * 🔥 CRITICAL: This resolves tenant via DATABASE, not Stripe API
     * Stripe API calls break multi-tenancy isolation and add single point of failure
     */
    private UUID resolveTenantFromCustomer(String customerId) {
        if (customerId == null || customerId.isBlank() || "unknown".equals(customerId)) {
            log.warn("[WEBHOOK] Cannot resolve tenant: customerId is unknown");
            return null;
        }

        try {
            var mapping = stripeCustomerRepository.findByStripeCustomerId(customerId);
            if (mapping.isPresent()) {
                UUID tenantId = mapping.get().getTenant().getId();
                log.info("[WEBHOOK] Resolved tenant {} for customer {}", tenantId, customerId);
                return tenantId;
            } else {
                log.warn("[WEBHOOK] Customer {} not mapped yet. Will retry on next event.", customerId);
                return null;
            }
        } catch (Exception e) {
            log.error("[WEBHOOK] Error resolving tenant for customer {}: {}", customerId, e.getMessage());
            return null;
        }
    }

    /**
     * Converte string tenantId para UUID, com tratamento de erros.
     * Retorna UUID zero se inválido.
     */
    private UUID parseTenantId(String tenantId) {
        try {
            if (tenantId != null && !tenantId.isBlank() && !"public".equals(tenantId)) {
                return UUID.fromString(tenantId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for tenantId: {}", tenantId);
        }
        // 🔥 SECURITY: System tenant UUID is FORBIDDEN - never use as fallback
        log.error("🔴 CRITICAL: parseTenantId() returning null tenant - check webhook payload!");
        throw new IllegalStateException("Cannot parse valid tenantId from webhook event");
    }

    /**
     * Método legado (mantido para compatibilidade).
     * @deprecated Use parseUUID() em vez disso.
     */
    @Deprecated
    private UUID parseUUID(String tenantId) {
        return parseTenantId(tenantId);
    }

    /**
     * Método legado (mantido para compatibilidade).
     * @deprecated Use idempotencyService.isDuplicate() em vez disso.
     */
    @Deprecated
    private boolean isEventAlreadyProcessed(String eventId) {
        return eventLogRepository.findByEventId(eventId)
                .map(log -> log.getStatus() == StripeEventLog.EventProcessingStatus.SUCCESS)
                .orElse(false);
    }

    /**
     * Método legado (mantido para compatibilidade).
     * @deprecated Use idempotencyService.markFailed() em vez disso.
     */
    @Deprecated
    private LocalDateTime calculateNextRetry(int retryCount) {
        long delay = Math.min((long) Math.pow(2, retryCount), 300);
        return LocalDateTime.now().plusSeconds(delay);
    }
}