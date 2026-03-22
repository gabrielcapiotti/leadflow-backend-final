package com.leadflow.backend.service.billing;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadflow.backend.entities.StripeEventLog;
import com.stripe.model.Event;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Structured JSON Logging Service for Stripe Webhooks
 * 
 * Provides JSON-formatted logging for webhook events that can be:
 * - Parsed by log aggregation tools (ELK, Datadog, Splunk, etc.)
 * - Analyzed programmatically
 * - Used for monitoring and alerting
 * 
 * Log Entry Structure:
 * {
 *   "timestamp": "2026-03-22T16:45:30.123Z",
 *   "eventId": "evt_xxxxx",
 *   "eventType": "charge.succeeded",
 *   "customerId": "cus_xxxxx",
 *   "tenantId": "tenant_1",
 *   "status": "processed",
 *   "action": "webhook_received|webhook_processed|webhook_failed|webhook_retry",
 *   "processingTimeMs": 245,
 *   "signatureValidated": true,
 *   "idempotencyChecked": true,
 *   "isDuplicate": false,
 *   "retryCount": 0,
 *   "errorMessage": null
 * }
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookLoggingService {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter ISO_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * Structured log entry for Stripe webhook events
     */
    @Data
    @Builder
    public static class WebhookLogEntry {
        
        private String timestamp;
        private String eventId;
        private String eventType;
        private String customerId;
        private String tenantId;
        private String status;
        private String action;
        private Long processingTimeMs;
        private Boolean signatureValidated;
        private Boolean idempotencyChecked;
        private Boolean isDuplicate;
        private Integer retryCount;
        private String errorMessage;
        private String source;

        public String toJson(ObjectMapper mapper) throws JsonProcessingException {
            return mapper.writeValueAsString(this);
        }
    }

    /**
     * Log webhook received event with signature validation
     * 
     * @param event Stripe Event
     * @param signatureValid Whether signature validation passed
     * @param customerId Customer ID from the event
     * @param processingTimeMs Time taken to process
     */
    public void logWebhookReceived(Event event, boolean signatureValid, String customerId, long processingTimeMs) {
        try {
            WebhookLogEntry entry = WebhookLogEntry.builder()
                .timestamp(getCurrentTimestamp())
                .eventId(event.getId())
                .eventType(event.getType())
                .customerId(customerId != null ? customerId : "unknown")
                .tenantId("public") // TODO: Extract from event metadata after Phase 2
                .status("received")
                .action("webhook_received")
                .processingTimeMs(processingTimeMs)
                .signatureValidated(signatureValid)
                .idempotencyChecked(false)
                .isDuplicate(false)
                .retryCount(0)
                .source("stripe")
                .build();

            String jsonLog = entry.toJson(objectMapper);
            log.info(jsonLog);

        } catch (JsonProcessingException e) {
            log.error("[LOGGING] Failed to serialize webhook received entry for event {}", event.getId(), e);
        }
    }

    /**
     * Log webhook processed successfully
     * 
     * @param event Stripe Event
     * @param isDuplicate Whether this was a duplicate event
     * @param processedAt When it was processed
     * @param processingTimeMs Time taken
     */
    public void logWebhookProcessed(Event event, boolean isDuplicate, LocalDateTime processedAt, long processingTimeMs) {
        try {
            String customerId = extractCustomerId(event);

            WebhookLogEntry entry = WebhookLogEntry.builder()
                .timestamp(getCurrentTimestamp())
                .eventId(event.getId())
                .eventType(event.getType())
                .customerId(customerId)
                .tenantId("public") // TODO: Extract from metadata after Phase 2
                .status(isDuplicate ? "duplicate" : "success")
                .action(isDuplicate ? "webhook_duplicate" : "webhook_processed")
                .processingTimeMs(processingTimeMs)
                .signatureValidated(true)
                .idempotencyChecked(true)
                .isDuplicate(isDuplicate)
                .retryCount(0)
                .source("stripe")
                .build();

            String jsonLog = entry.toJson(objectMapper);
            log.info(jsonLog);

        } catch (JsonProcessingException e) {
            log.error("[LOGGING] Failed to serialize webhook processed entry for event {}", event.getId(), e);
        }
    }

    /**
     * Log webhook processing failure
     * 
     * @param eventId Event ID that failed
     * @param eventType Type of event
     * @param errorMessage Error description
     * @param processingTimeMs Time taken before failure
     */
    public void logWebhookFailed(String eventId, String eventType, String errorMessage, long processingTimeMs) {
        try {
            WebhookLogEntry entry = WebhookLogEntry.builder()
                .timestamp(getCurrentTimestamp())
                .eventId(eventId)
                .eventType(eventType)
                .customerId("unknown")
                .tenantId("public")
                .status("failed")
                .action("webhook_failed")
                .processingTimeMs(processingTimeMs)
                .signatureValidated(false)
                .idempotencyChecked(false)
                .isDuplicate(false)
                .retryCount(0)
                .errorMessage(errorMessage)
                .source("stripe")
                .build();

            String jsonLog = entry.toJson(objectMapper);
            log.error(jsonLog);

        } catch (JsonProcessingException e) {
            log.error("[LOGGING] Failed to serialize webhook failed entry for event {}", eventId, e);
        }
    }

    /**
     * Log webhook retry attempt
     * 
     * @param eventLog StripeEventLog entity with retry information
     * @param attempt Current retry attempt number
     * @param nextRetryAt When the next retry is scheduled
     * @param errorMessage Error from previous attempt
     */
    public void logWebhookRetry(StripeEventLog eventLog, int attempt, LocalDateTime nextRetryAt, String errorMessage) {
        try {
            long nextRetryInSeconds = 0;
            if (nextRetryAt != null) {
                nextRetryInSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), nextRetryAt);
            }

            String delayStr = nextRetryInSeconds > 0 ? 
                String.format("retry in %ds", nextRetryInSeconds) : 
                "retry immediate";

            WebhookLogEntry entry = WebhookLogEntry.builder()
                .timestamp(getCurrentTimestamp())
                .eventId(eventLog.getEventId())
                .eventType(eventLog.getEventType())
                .customerId("unknown")
                .tenantId("public")
                .status("retry_pending")
                .action("webhook_retry_scheduled")
                .processingTimeMs(null)
                .signatureValidated(true)
                .idempotencyChecked(true)
                .isDuplicate(false)
                .retryCount(attempt)
                .errorMessage(String.format("%s - %s", errorMessage, delayStr))
                .source("stripe")
                .build();

            String jsonLog = entry.toJson(objectMapper);
            log.warn(jsonLog);

        } catch (Exception e) {
            log.error("[LOGGING] Failed to serialize webhook retry entry for event {}", eventLog.getEventId(), e);
        }
    }

    /**
     * Log webhook retry success
     * 
     * @param eventLog StripeEventLog that was retried
     * @param totalAttempts Total retry attempts made
     * @param totalTimeMs Total time from first attempt to success
     */
    public void logWebhookRetrySuccess(StripeEventLog eventLog, int totalAttempts, long totalTimeMs) {
        try {
            WebhookLogEntry entry = WebhookLogEntry.builder()
                .timestamp(getCurrentTimestamp())
                .eventId(eventLog.getEventId())
                .eventType(eventLog.getEventType())
                .customerId("unknown")
                .tenantId("public")
                .status("success")
                .action("webhook_retry_success")
                .processingTimeMs(totalTimeMs)
                .signatureValidated(true)
                .idempotencyChecked(true)
                .isDuplicate(false)
                .retryCount(totalAttempts)
                .errorMessage(null)
                .source("stripe")
                .build();

            String jsonLog = entry.toJson(objectMapper);
            log.info(jsonLog);

        } catch (JsonProcessingException e) {
            log.error("[LOGGING] Failed to serialize webhook retry success entry for event {}", eventLog.getEventId(), e);
        }
    }

    /**
     * Log webhook retry permanent failure
     * 
     * @param eventLog StripeEventLog that failed permanently
     * @param totalAttempts Total attempts made
     * @param lastError Last error message
     */
    public void logWebhookRetryPermanentFailure(StripeEventLog eventLog, int totalAttempts, String lastError) {
        try {
            WebhookLogEntry entry = WebhookLogEntry.builder()
                .timestamp(getCurrentTimestamp())
                .eventId(eventLog.getEventId())
                .eventType(eventLog.getEventType())
                .customerId("unknown")
                .tenantId("public")
                .status("failed_permanent")
                .action("webhook_failed_permanent")
                .processingTimeMs(null)
                .signatureValidated(true)
                .idempotencyChecked(true)
                .isDuplicate(false)
                .retryCount(totalAttempts)
                .errorMessage(String.format("Permanent failure after %d retries: %s", totalAttempts, lastError))
                .source("stripe")
                .build();

            String jsonLog = entry.toJson(objectMapper);
            log.error(jsonLog);

        } catch (JsonProcessingException e) {
            log.error("[LOGGING] Failed to serialize webhook permanent failure entry for event {}", eventLog.getEventId(), e);
        }
    }

    /**
     * Log signature validation failure
     * 
     * @param signatureHeader The signature header that failed
     * @param errorMessage Validation error
     */
    public void logSignatureValidationFailure(String signatureHeader, String errorMessage) {
        try {
            WebhookLogEntry entry = WebhookLogEntry.builder()
                .timestamp(getCurrentTimestamp())
                .eventId("unknown")
                .eventType("unknown")
                .customerId("unknown")
                .tenantId("public")
                .status("signature_validation_failed")
                .action("webhook_signature_invalid")
                .processingTimeMs(null)
                .signatureValidated(false)
                .idempotencyChecked(false)
                .isDuplicate(false)
                .retryCount(0)
                .errorMessage(errorMessage)
                .source("stripe")
                .build();

            String jsonLog = entry.toJson(objectMapper);
            log.error(jsonLog);

        } catch (JsonProcessingException e) {
            log.error("[LOGGING] Failed to serialize signature validation failure entry", e);
        }
    }

    /**
     * Extract customer ID from Stripe Event
     */
    private String extractCustomerId(Event event) {
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
            log.debug("Could not extract customer from event {}", event.getId());
            return "unknown";
        }
    }

    /**
     * Get current timestamp in ISO format
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(ISO_FORMATTER);
    }
}
