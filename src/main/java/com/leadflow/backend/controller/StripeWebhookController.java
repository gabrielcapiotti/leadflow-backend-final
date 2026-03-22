package com.leadflow.backend.controller;

import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.service.billing.*;
import com.leadflow.backend.service.vendor.SubscriptionService;
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
    private final SubscriptionService subscriptionService;
    private final StripeWebhookProcessingService webhookProcessingService;
    private final StripeWebhookProcessor webhookProcessor;
    private final StripeEventLogRepository eventLogRepository;
    private final WebhookLoggingService webhookLoggingService;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) throws IOException {

        long startTime = System.currentTimeMillis();
        StripeEventLog eventLog = null;

        String payload = request.getReader()
                .lines()
                .collect(Collectors.joining());

        String signatureHeader = request.getHeader("Stripe-Signature");

        try {
            log.info("[WEBHOOK] Received Stripe webhook");

            // 1. VALIDAR ASSINATURA
            Event event = stripeService.constructWebhookEvent(payload, signatureHeader);

            // 2. EXTRAIR DADOS
            String customerId = extractCustomerIdFromEvent(event);
            String tenantId = stripeService.extractTenantIdFromEvent(event);

            long duration = System.currentTimeMillis() - startTime;
            webhookLoggingService.logWebhookReceived(event, true, customerId, duration);

            // 3. SALVAR EVENTO (PENDING)
            UUID tenantUuid = null;
            try {
                if (tenantId != null && !"unknown".equals(tenantId)) {
                    tenantUuid = UUID.fromString(tenantId);
                }
            } catch (IllegalArgumentException e) {
                log.warn("[WEBHOOK] Invalid UUID format for tenantId: {}", tenantId);
            }
            
            eventLog = StripeEventLog.builder()
                    .eventId(event.getId())
                    .eventType(event.getType())
                    .customerId(customerId)
                    .tenantId(tenantUuid)
                    .payload(payload)
                    .status(StripeEventLog.EventProcessingStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(3)
                    .build();

            eventLog = eventLogRepository.save(eventLog);

            // 4. IDEMPOTÊNCIA
            if (isEventAlreadyProcessed(event.getId())) {
                eventLog.setStatus(StripeEventLog.EventProcessingStatus.SUCCESS);
                eventLogRepository.save(eventLog);

                webhookLoggingService.logWebhookProcessed(
                        event,
                        true,
                        LocalDateTime.now(),
                        duration
                );

                return ResponseEntity.ok("{}");
            }

            // 5. PROCESSAMENTO COM TENANT
            try {
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContext.setTenant(tenantId);
                } else {
                    TenantContext.setTenant("public");
                }

                webhookProcessor.process(event);

                eventLog.setStatus(StripeEventLog.EventProcessingStatus.SUCCESS);
                eventLog.setProcessedAt(LocalDateTime.now());
                eventLogRepository.save(eventLog);

                webhookLoggingService.logWebhookProcessed(
                        event,
                        false,
                        LocalDateTime.now(),
                        duration
                );

            } catch (Exception e) {

                eventLog.setStatus(StripeEventLog.EventProcessingStatus.RETRY_PENDING);
                eventLog.setLastError(e.getMessage());
                eventLog.setNextRetryAt(calculateNextRetry(0));
                eventLogRepository.save(eventLog);

                webhookLoggingService.logWebhookFailed(
                        event.getId(),
                        event.getType(),
                        e.getMessage(),
                        duration
                );

            } finally {
                TenantContext.clear();
            }

            webhookProcessingService.processAndLogEvent(event);

            return ResponseEntity.ok("{}");

        } catch (Exception e) {

            log.error("[WEBHOOK] Error processing webhook", e);

            if (eventLog != null) {
                eventLog.setStatus(StripeEventLog.EventProcessingStatus.FAILED);
                eventLog.setLastError(e.getMessage());
                eventLogRepository.save(eventLog);
            }

            return ResponseEntity.status(401).body("Invalid webhook");
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        return eventLogRepository.findByEventId(eventId)
                .map(log -> log.getStatus() == StripeEventLog.EventProcessingStatus.SUCCESS)
                .orElse(false);
    }

    private LocalDateTime calculateNextRetry(int retryCount) {
        long delay = Math.min((long) Math.pow(2, retryCount), 300);
        return LocalDateTime.now().plusSeconds(delay);
    }

    private String extractCustomerIdFromEvent(Event event) {
        try {
            if (event.getData() != null && event.getData().getObject() != null) {
                Object obj = event.getData().getObject();

                if (obj instanceof com.stripe.model.Customer c) return c.getId();
                if (obj instanceof com.stripe.model.Charge c) return c.getCustomer();
                if (obj instanceof com.stripe.model.Invoice i) return i.getCustomer();
                if (obj instanceof com.stripe.model.Subscription s) return s.getCustomer();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}