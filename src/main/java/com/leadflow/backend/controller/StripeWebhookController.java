package com.leadflow.backend.controller;

import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.exception.StripeSignatureVerificationException;
import com.leadflow.backend.exception.StripeTimestampExpiredException;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.service.billing.StripeService;
import com.leadflow.backend.service.billing.StripeWebhookProcessor;
import com.leadflow.backend.service.billing.StripeWebhookValidator;
import com.leadflow.backend.service.billing.StripeWebhookProcessingService;
import com.leadflow.backend.service.billing.WebhookLoggingService;
import com.leadflow.backend.service.vendor.SubscriptionService;
import com.stripe.model.Event;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Stripe Webhook Controller
 * 
 * Handles incoming webhooks from Stripe with security validation:
 * 1. HMAC-SHA256 signature verification
 * 2. Timestamp validation (prevent replay attacks)
 * 3. Event processing with handlers
 * 4. Event persistence for auditoria and replay
 */
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

        String payload = request.getReader()
                .lines()
                .collect(Collectors.joining());

        String signatureHeader = request.getHeader("Stripe-Signature");

        try {
            log.info("[CONTROLLER] Received Stripe webhook");
            log.info("[CONTROLLER] Signature header: {}", signatureHeader != null ? "present" : "missing");
            
            // Single validation using Stripe SDK's Webhook.constructEvent()
            // This handles both signature and timestamp validation properly
            Event event = stripeService.constructWebhookEvent(payload, signatureHeader);
            log.info("[CONTROLLER] ✅ Webhook signature and timestamp validated by Stripe SDK");
            log.info("✅ Stripe event deserialized: type={}, id={}", event.getType(), event.getId());
            
            long processingTime = System.currentTimeMillis() - startTime;
            String customerId = extractCustomerIdFromEvent(event);
            webhookLoggingService.logWebhookReceived(event, true, customerId, processingTime);
            
            // Step 2: Check idempotency - is this event already processed?
            if (isEventAlreadyProcessed(event.getId())) {
                log.warn("⚠️  Duplicate webhook event received (idempotency): {}", event.getId());
                webhookLoggingService.logWebhookProcessed(event, true, LocalDateTime.now(), System.currentTimeMillis() - startTime);
                recordWebhookEvent(event, true, "DUPLICATE_EVENT");
                return ResponseEntity.ok("received"); // Return 200 even for duplicates
            }
            
            // Step 3: Process the event through handler registry
            try {
                webhookProcessor.process(event);
                log.info("✅ Webhook event processed successfully: id={}", event.getId());
                long processingTimeMs = System.currentTimeMillis() - startTime;
                webhookLoggingService.logWebhookProcessed(event, false, LocalDateTime.now(), processingTimeMs);
                recordWebhookEvent(event, true, null);
            } catch (Exception e) {
                log.warn("⚠️  Event processing completed with exception (will retry), id={}", event.getId(), e);
                webhookLoggingService.logWebhookFailed(event.getId(), event.getType(), e.getMessage(), System.currentTimeMillis() - startTime);
                recordWebhookEvent(event, false, e.getMessage());
                // Return 200 anyway so Stripe doesn't retry immediately
                return ResponseEntity.ok("received");
            }
            
            // Step 4: Also call legacy processing service for backward compatibility
            webhookProcessingService.processAndLogEvent(event);
            
            return ResponseEntity.ok("received");
            
        } catch (RuntimeException e) {
            // Catches SignatureVerificationException (wrapped as RuntimeException by StripeService)
            // and timestamp validation errors from Stripe SDK
            if (e.getMessage() != null && e.getMessage().contains("signature")) {
                log.error("❌ Webhook signature verification failed: {}", e.getMessage());
                webhookLoggingService.logSignatureValidationFailure(signatureHeader, e.getMessage());
                recordWebhookEvent(null, false, "SIGNATURE_VERIFICATION_FAILED: " + e.getMessage());
                return ResponseEntity.status(401).body("Invalid signature");
            } else if (e.getMessage() != null && e.getMessage().contains("timestamp")) {
                log.error("❌ Webhook timestamp validation failed: {}", e.getMessage());
                webhookLoggingService.logSignatureValidationFailure(signatureHeader, "Timestamp invalid: " + e.getMessage());
                recordWebhookEvent(null, false, "TIMESTAMP_VERIFICATION_FAILED: " + e.getMessage());
                return ResponseEntity.status(401).body("Timestamp expired");
            }
            // Generic error handling
            log.error("❌ Failed to process webhook", e);
            recordWebhookEvent(null, false, "PROCESSING_EXCEPTION: " + e.getMessage());
            // Return 200 anyway so Stripe doesn't keep retrying
            return ResponseEntity.ok("received");
        } catch (Exception e) {
            log.error("❌ Failed to process webhook", e);
            recordWebhookEvent(null, false, "PROCESSING_EXCEPTION: " + e.getMessage());
            // Return 200 anyway so Stripe doesn't keep retrying
            return ResponseEntity.ok("received");
        }
    }

    /**
     * Check if this event was already processed (idempotency).
     * Prevents duplicate processing if Stripe retries the webhook.
     */
    private boolean isEventAlreadyProcessed(String eventId) {
        return eventLogRepository.findByEventId(eventId)
                .map(log -> log.getStatus() == StripeEventLog.EventProcessingStatus.SUCCESS)
                .orElse(false);
    }

    /**
     * Extract customer ID from Stripe Event
     */
    private String extractCustomerIdFromEvent(Event event) {
        try {
            if (event.getData() != null && event.getData().getObject() != null) {
                Object obj = event.getData().getObject();
                if (obj instanceof com.stripe.model.Customer) {
                    return ((com.stripe.model.Customer) obj).getId();
                } else if (obj instanceof com.stripe.model.Charge) {
                    return ((com.stripe.model.Charge) obj).getCustomer();
                } else if (obj instanceof com.stripe.model.Invoice) {
                    return ((com.stripe.model.Invoice) obj).getCustomer();
                } else if (obj instanceof com.stripe.model.Subscription) {
                    return ((com.stripe.model.Subscription) obj).getCustomer();
                }
            }
            return "unknown";
        } catch (Exception e) {
            log.debug("Could not extract customer ID from event");
            return "unknown";
        }
    }

    /**
     * Record webhook event in database for auditoria and replay capability.
     */
    private void recordWebhookEvent(Event event, boolean success, String errorMessage) {
        try {
            StripeEventLog eventLog = StripeEventLog.builder()
                    .eventId(event != null ? event.getId() : "unknown")
                    .eventType(event != null ? event.getType() : "unknown")
                    .payload(event != null ? event.toString() : "{}")
                    .status(success ? StripeEventLog.EventProcessingStatus.SUCCESS : StripeEventLog.EventProcessingStatus.FAILED)
                    .retryCount(0)
                    .maxRetries(3)
                    .lastError(errorMessage)
                    .processedAt(LocalDateTime.now())
                    .build();
            
            eventLogRepository.save(eventLog);
            log.debug("Recorded webhook event: id={}, status={}", eventLog.getEventId(), eventLog.getStatus());
        } catch (Exception e) {
            log.error("Failed to record webhook event in database", e);
            // Don't fail the webhook handling if we can't save the log
        }
    }
}
