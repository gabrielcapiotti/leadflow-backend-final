package com.leadflow.backend.controller.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadflow.backend.entities.vendor.EmailEvent;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.EmailEventRepository;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.repository.tenant.TenantRepository;
import com.leadflow.backend.service.notification.SendGridWebhookVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SendGrid Webhook Controller
 * 
 * Handles webhook events from SendGrid with proper multi-tenant context management.
 * 
 * ⚠️ IMPORTANT: This controller receives unauthenticated external webhooks,
 * so we must manually resolve and set TenantContext for each event.
 * 
 * Pattern:
 * 1. Validate webhook signature
 * 2. Extract email from event
 * 3. Resolve tenant via email → vendor mapping
 * 4. Manually set TenantContext
 * 5. Persist event
 * 6. Clear TenantContext
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/sendgrid")
public class SendGridWebhookController {

    private static final String SIGNATURE_HEADER = "X-Twilio-Email-Event-Webhook-Signature";
    private static final String TIMESTAMP_HEADER = "X-Twilio-Email-Event-Webhook-Timestamp";

    private final EmailEventRepository repository;
    private final VendorRepository vendorRepository;
    private final TenantRepository tenantRepository;
    private final SendGridWebhookVerifier verifier;
    private final ObjectMapper objectMapper;

    public SendGridWebhookController(EmailEventRepository repository,
                                     VendorRepository vendorRepository,
                                     TenantRepository tenantRepository,
                                     SendGridWebhookVerifier verifier,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.vendorRepository = vendorRepository;
        this.tenantRepository = tenantRepository;
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestBody String payload,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp
    ) {

        if (verifier.isVerificationEnabled()
            && !verifier.verify(signature, timestamp, payload)) {
            log.warn("[SendGrid] Webhook signature verification failed");
            return ResponseEntity.status(401).build();
        }

        List<Map<String, Object>> events;
        try {
            events = objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("[SendGrid] Invalid JSON payload: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }

        for (Map<String, Object> event : events) {
            String email = stringValue(event.get("email"));
            String eventType = stringValue(event.get("event"));

            if (email == null || eventType == null) {
                log.debug("[SendGrid] Skipping event: missing email or eventType");
                continue;
            }

            // ================================================================
            // RESOLVE TENANT: Try metadata first (OPÇÃO 1 - RECOMENDADO)
            // Strategy: custom_args.tenant_id → email fallback
            // ================================================================
            Optional<UUID> tenantIdOpt = resolveTenantFromMetadata(event)
                    .or(() -> resolveTenantFromEmail(email));
            
            if (tenantIdOpt.isEmpty()) {
                log.warn("[SendGrid] Cannot resolve tenant for email: {}. " +
                        "Expected custom_args.tenant_id in payload or email in system. " +
                        "Skipping event.", email);
                continue;
            }

            UUID tenantId = tenantIdOpt.get();

            // ================================================================
            // VALIDATE TENANT EXISTS (SaaS integrity - CRITICAL)
            // Prevents silently persisting events to wrong/fake tenant
            // ================================================================
            if (!tenantRepository.findByIdAndDeletedAtIsNull(tenantId).isPresent()) {
                log.warn("[SendGrid] Tenant does not exist in system: {}. " +
                        "Rejecting invalid tenant context. Email: {}. Skipping event.", 
                        tenantId, email);
                continue;
            }

            try {
                // ================================================
                // MANUALLY SET TENANT CONTEXT (webhook has no auth)
                // ================================================
                TenantContext.setTenant(tenantId);
                log.debug("[SendGrid] TenantContext set for tenant: {}", tenantId);

                // ================================================
                // PERSIST EVENT (within tenant context)
                // ================================================
                EmailEvent emailEvent = new EmailEvent();
                emailEvent.setEmail(email);
                emailEvent.setEventType(eventType);
                emailEvent.setOccurredAt(resolveOccurredAt(event));
                emailEvent.setReason(stringValue(event.get("reason")));

                repository.save(emailEvent);
                log.info("[SendGrid] Event persisted: email={}, type={}, tenant={}", 
                        email, eventType, tenantId);

            } catch (Exception ex) {
                log.error("[SendGrid] Error processing event for email: {}", email, ex);
            } finally {
                // ================================================
                // CLEANUP TENANT CONTEXT (prevent leaks)
                // ================================================
                TenantContext.clear();
            }
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Resolve tenant from SendGrid metadata (OPÇÃO 1 - RECOMENDADO).
     * Strategy: Extract tenant_id from custom_args
     * 
     * Example payload:
     * {
     *   "email": "user@example.com",
     *   "custom_args": {
     *     "tenant_id": "550e8400-e29b-41d4-a716-446655440000"
     *   }
     * }
     */
    private Optional<UUID> resolveTenantFromMetadata(Map<String, Object> event) {
        try {
            Object customArgsObj = event.get("custom_args");
            
            if (!(customArgsObj instanceof Map)) {
                return Optional.empty();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> customArgs = (Map<String, Object>) customArgsObj;
            String tenantIdStr = stringValue(customArgs.get("tenant_id"));
            
            if (tenantIdStr == null) {
                return Optional.empty();
            }
            
            UUID tenantId = UUID.fromString(tenantIdStr);
            log.debug("[SendGrid] Tenant resolved via metadata: {}", tenantId);
            return Optional.of(tenantId);
            
        } catch (IllegalArgumentException ex) {
            log.warn("[SendGrid] Invalid UUID in custom_args.tenant_id: {}", ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("[SendGrid] Error resolving tenant from metadata: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolve tenant from email address (OPÇÃO 2 - fallback).
     * Strategy: email → vendor → tenantId
     * 
     * This is a fallback for webhooks sent without metadata.
     * Deterministic but relies on email existing in system.
     */
    private Optional<UUID> resolveTenantFromEmail(String email) {
        try {
            return vendorRepository.findByUserEmail(email)
                    .stream()
                    .findFirst()
                    .map(vendor -> {
                        log.debug("[SendGrid] Tenant resolved via email: {} → tenant: {}", 
                                email, vendor.getId());
                        return vendor.getId();
                    });
        } catch (Exception ex) {
            log.warn("[SendGrid] Error resolving tenant from email: {}", email, ex);
            return Optional.empty();
        }
    }

    private Instant resolveOccurredAt(Map<String, Object> event) {
        Object timestamp = event.get("timestamp");

        if (timestamp instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }

        return Instant.now();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }

        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }
}
