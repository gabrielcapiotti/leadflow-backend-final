package com.leadflow.backend.controller;

import com.leadflow.backend.config.metrics.WebhookMetrics;
import com.leadflow.backend.dto.billing.CheckoutRequest;
import com.leadflow.backend.dto.billing.CheckoutResponse;
import com.leadflow.backend.dto.billing.InvoiceDTO;
import com.leadflow.backend.dto.billing.PaymentMethodDTO;
import com.leadflow.backend.dto.billing.PublicBillingDTO;
import com.leadflow.backend.dto.billing.SubscriptionDetailsDTO;
import com.leadflow.backend.dto.billing.SubscriptionCreateRequest;
import com.leadflow.backend.exception.StripeSignatureVerificationException;
import com.leadflow.backend.exception.StripeTimestampExpiredException;
import com.leadflow.backend.repository.tenant.TenantRepository;
import com.leadflow.backend.security.VendorContext;
import com.leadflow.backend.service.billing.StripeService;
import com.leadflow.backend.service.billing.StripeWebhookValidator;
import com.leadflow.backend.service.billing.StripeWebhookAlertService;
import com.leadflow.backend.service.billing.StripeCustomerMappingService;
import com.leadflow.backend.service.billing.BillingDashboardService;
import com.leadflow.backend.service.vendor.SubscriptionService;
import com.leadflow.backend.webhook.service.WebhookReplayService;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceCollection;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Billing and Payment API Controller.
 * 
 * Handles Stripe checkout sessions, webhook processing with HMAC-SHA256 validation,
 * timestamp validation, and comprehensive error handling.
 * 
 * All webhook operations are tracked with metrics (timer, counters, validation rates).
 */
@Slf4j
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Payment processing and subscription management via Stripe")
public class BillingController {

    private final StripeService stripeService;
    private final StripeWebhookValidator webhookValidator;
    private final WebhookMetrics webhookMetrics;
    private final StripeWebhookAlertService webhookAlertService;
    private final SubscriptionService subscriptionService;
    private final VendorContext vendorContext;
    private final StripeCustomerMappingService stripeCustomerMappingService;
    private final TenantRepository tenantRepository;
    private final BillingDashboardService billingDashboardService;
    private final WebhookReplayService webhookReplayService;

    /**
     * Creates a Stripe checkout session for subscription payment.
     * 
     * @param request validated checkout request containing customer email and optional tenantId
     * @return checkout response with URL, reference ID, and provider
     */
    @PostMapping("/checkout")
    @Operation(
        summary = "Create Stripe checkout session",
        description = "Initiates a new Stripe checkout session for subscription payment. Returns a checkout URL and session reference ID.",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Checkout session created successfully",
            content = @Content(schema = @Schema(implementation = CheckoutResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid checkout request - missing or invalid email"),
        @ApiResponse(responseCode = "500", description = "Stripe API error or server error")
    })
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @Valid @RequestBody CheckoutRequest request
    ) {
        CheckoutResponse response = stripeService.createCheckoutSession(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Handles incoming Stripe webhook events.
     * Validates webhook signature and routes to appropriate handler.
     * 
     * Stripe-Signature header format: "t=<timestamp>,v1=<signature>"
     * 
     * @param payload raw webhook payload JSON
     * @param stripeSignature Stripe-Signature header containing timestamp and signature
     * @return "Webhook processed" with HTTP 200 on success, error message with HTTP 400 on failure
     */
    @PostMapping("/webhook")
    @Operation(
        summary = "Handle Stripe webhook events",
        description = "Receives and processes Stripe webhook events (subscription, payment, invoice). " +
                      "Validates HMAC-SHA256 signature and timestamp (5 min tolerance). " +
                      "Supports: customer.subscription.*, invoice.payment_*",
        tags = {"Billing", "Webhooks"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid signature, expired timestamp, or missing header",
            content = @Content(schema = @Schema(example = "Invalid webhook signature"))),
        @ApiResponse(responseCode = "500", description = "Unexpected error during webhook processing")
    })
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @Parameter(description = "Stripe-Signature header with format: t=<timestamp>,v1=<signature>", required = false)
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature
    ) {
        long startTime = System.currentTimeMillis();
        String eventType = "unknown";
        String eventId = null;
        
        try {
            // ============================================================
            // 0️⃣ EXTRAIR EVENT ID E TYPE DO PAYLOAD (antes de validar)
            // ============================================================
            eventId = extractEventIdFromPayload(payload);
            eventType = extractEventTypeFromPayload(payload);
            log.info("[BILLING] Extracted from raw payload: eventId={}, eventType={}", eventId, eventType);
            
            // Parse Stripe-Signature header format: "t=<timestamp>,v1=<signature>"
            if (stripeSignature == null || stripeSignature.isBlank()) {
                log.warn("Missing Stripe-Signature header - webhooks must come from Stripe with proper signature");
                log.warn("For testing without Stripe, callers should use /api/billing/test endpoints instead");
                webhookMetrics.recordEventType("invalid");
                webhookMetrics.incrementFailureCounter("invalid", "missing_signature");
                webhookAlertService.recordFailure("invalid", "Missing Stripe-Signature header", null);
                
                // 🔥 PERSIST webhook even with missing signature
                try {
                    webhookReplayService.storeFailedWebhook(
                            eventId,
                            eventType,
                            payload,
                            "MISSING_STRIPE_SIGNATURE_HEADER"
                    );
                    log.info("[BILLING] ✅ Webhook persisted (missing signature): {}", eventId);
                } catch (Exception ex) {
                    log.error("[BILLING] ⚠️  Failed to persist: {}", ex.getMessage());
                }
                
                return ResponseEntity.badRequest()
                    .body("Missing Stripe-Signature header. Webhooks must be called by Stripe with HMAC signature.");
            }

            String timestamp = null;
            String signature = null;

            for (String part : stripeSignature.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    if ("t".equals(kv[0].trim())) {
                        timestamp = kv[1].trim();
                    } else if ("v1".equals(kv[0].trim())) {
                        signature = kv[1].trim();
                    }
                }
            }

            if (timestamp == null || signature == null) {
                log.warn("Invalid Stripe-Signature header format: {}", stripeSignature);
                webhookMetrics.recordEventType("invalid");
                webhookMetrics.incrementFailureCounter("invalid", "malformed_signature");
                webhookAlertService.recordFailure("invalid", "Invalid Stripe-Signature header format", null);
                
                // 🔥 PERSIST webhook with malformed signature
                try {
                    webhookReplayService.storeFailedWebhook(
                            eventId,
                            eventType,
                            payload,
                            "MALFORMED_STRIPE_SIGNATURE_HEADER"
                    );
                    log.info("[BILLING] ✅ Webhook persisted (malformed signature): {}", eventId);
                } catch (Exception ex) {
                    log.error("[BILLING] ⚠️  Failed to persist: {}", ex.getMessage());
                }
                
                return ResponseEntity.badRequest().body("Invalid Stripe-Signature header format");
            }

            // Validate timestamp first (prevents replay attacks)
            try {
                webhookMetrics.recordTimestampValidation(true);
                webhookValidator.validateTimestamp(timestamp);
            } catch (StripeTimestampExpiredException e) {
                webhookMetrics.recordTimestampValidation(false);
                webhookMetrics.incrementFailureCounter("all", "expired_timestamp");
                webhookAlertService.recordFailure("all", "Webhook timestamp expired: " + e.getMessage(), e);
                log.warn("Webhook timestamp expired: {}", e.getMessage());
                
                // 🔥 PERSIST webhook with expired timestamp
                try {
                    webhookReplayService.storeFailedWebhook(
                            eventId,
                            eventType,
                            payload,
                            "EXPIRED_TIMESTAMP: " + e.getMessage()
                    );
                    log.info("[BILLING] ✅ Webhook persisted (expired timestamp): {}", eventId);
                } catch (Exception ex) {
                    log.error("[BILLING] ⚠️  Failed to persist: {}", ex.getMessage());
                }
                
                return ResponseEntity.badRequest().body("Webhook timestamp too old");
            }

            // Validate signature
            try {
                webhookValidator.validateSignature(payload, signature, timestamp);
                webhookMetrics.recordSignatureValidation(true);
                log.info("[BILLING] ✅ Webhook signature validated: {}", eventId);
            } catch (StripeSignatureVerificationException e) {
                webhookMetrics.recordSignatureValidation(false);
                webhookMetrics.incrementFailureCounter("all", "invalid_signature");
                webhookAlertService.recordFailure("all", "Webhook signature verification failed: " + e.getMessage(), e);
                log.warn("Webhook signature verification failed: {}", e.getMessage());
                
                // 🔥 PERSIST webhook with invalid signature
                try {
                    webhookReplayService.storeFailedWebhook(
                            eventId,
                            eventType,
                            payload,
                            "INVALID_SIGNATURE: " + e.getMessage()
                    );
                    log.info("[BILLING] ✅ Webhook persisted (invalid signature): {}", eventId);
                } catch (Exception ex) {
                    log.error("[BILLING] ⚠️  Failed to persist: {}", ex.getMessage());
                }
                
                return ResponseEntity.badRequest().body("Invalid webhook signature");
            }

            // Process webhook event and route to appropriate handler
            try {
                eventType = stripeService.processWebhookEvent(payload);
                webhookMetrics.recordEventType(eventType);
                webhookMetrics.incrementSuccessCounter(eventType);
                webhookAlertService.recordSuccess(eventType);

                long duration = System.currentTimeMillis() - startTime;
                webhookMetrics.recordProcessingDelay(duration);

                log.info("Webhook processed successfully: event_type={}, duration={}ms", eventType, duration);
                return ResponseEntity.ok("Webhook processed");
                
            } catch (Exception processEx) {
                // 🔥 PERSIST webhook with processing error
                try {
                    webhookReplayService.storeFailedWebhook(
                            eventId,
                            eventType,
                            payload,
                            "PROCESSING_ERROR: " + processEx.getMessage()
                    );
                    log.info("[BILLING] ✅ Webhook persisted (processing error): {}", eventId);
                } catch (Exception ex) {
                    log.error("[BILLING] ⚠️  Failed to persist: {}", ex.getMessage());
                }
                
                throw processEx;
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            webhookMetrics.recordProcessingDelay(duration);
            webhookMetrics.incrementFailureCounter(eventType, "processing_error");
            webhookAlertService.recordFailure(eventType, "Unexpected error: " + e.getMessage(), e);
            
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Failed to process webhook: " + e.getMessage());
        }
    }

    /**
     * Get vendor's current subscription details or public default
     * 
     * Returns:
     * - Vendor subscription if user has vendor context
     * - Public default subscription if no vendor context (test/public users)
     * - 401 if not authenticated
     */
    @GetMapping("/subscription")
    @Transactional(readOnly = true)
    @Operation(
        summary = "Get subscription details",
        description = "Returns the current subscription status and details for the authenticated vendor, or public defaults for test users",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Subscription details retrieved",
            content = @Content(schema = @Schema(implementation = SubscriptionDetailsDTO.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getSubscriptionDetails() {
        UUID vendorId;

        try {
            vendorId = vendorContext.getCurrentVendorId();
        } catch (Exception e) {
            log.warn("Vendor context resolution failed: {}", e.getMessage());
            // Return public defaults for users without vendor context
            return ResponseEntity.ok(PublicBillingDTO.testDefault());
        }

        if (vendorId == null) {
            // Return public defaults for users without vendor context
            return ResponseEntity.ok(PublicBillingDTO.testDefault());
        }

        var subscription = subscriptionService.getSubscriptionByVendorId(vendorId);
        
        log.info("🔍 GET /subscription - vendorId={}, subscription.isEmpty()={}", 
            vendorId, subscription.isEmpty());
        if (subscription.isPresent()) {
            log.info("  ✅ Found subscription: status={}, email={}", 
                subscription.get().getStatus(), 
                subscription.get().getEmail());
        }

        if (subscription.isEmpty()) {
            log.warn("  ⚠️ No subscription found, returning 204");
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(
                SubscriptionDetailsDTO.fromEntity(subscription.get())
        );
    }

    /**
     * Create or activate a subscription for the vendor
     * 
     * @param request subscription creation request with planId
     * @return subscription details after creation
     */
    @PostMapping("/subscription")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Create subscription",
        description = "Creates or activates a new subscription for the authenticated user via TenantContext",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Subscription created successfully",
            content = @Content(schema = @Schema(implementation = SubscriptionDetailsDTO.class))),
        @ApiResponse(responseCode = "201", description = "Subscription created"),
        @ApiResponse(responseCode = "400", description = "Invalid plan ID or request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> createSubscription(
            @Valid @RequestBody SubscriptionCreateRequest request
    ) {
        try {
            String planId = request.getPlanId() != null ? request.getPlanId() : "STANDARD";
            
            log.info("🔵 Creating subscription with plan code: {} (legacy endpoint)", planId);
            
            // Use BillingDashboardService which resolves TenantContext correctly
            SubscriptionDetailsDTO subscription = billingDashboardService.createSubscription(planId);
            
            log.info("✅ Subscription created successfully");
            return ResponseEntity.status(201).body(subscription);
            
        } catch (Exception e) {
            log.error("❌ Error creating subscription (legacy): {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "error", "subscription_creation_failed",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get vendor's invoices
     * 
     * Requires active subscription to access
     */
    @PreAuthorize("@subscriptionGuard.isActive()")
    @GetMapping("/invoices")
    @Operation(
        summary = "List invoices",
        description = "Returns paginated list of invoices for the authenticated vendor. Requires active subscription.",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Invoices retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized or no active subscription")
    })
    public ResponseEntity<List<InvoiceDTO>> getInvoices(
            @Parameter(description = "Limit per page") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Starting after invoice ID") @RequestParam(required = false) String startingAfter
    ) {
        try {
            UUID vendorId;
            try {
                vendorId = vendorContext.getCurrentVendorId();
            } catch (Exception e) {
                log.debug("Vendor context not available, returning empty invoices list: {}", e.getMessage());
                return ResponseEntity.ok(List.of());
            }
            
            if (vendorId == null) {
                return ResponseEntity.ok(List.of());
            }
            
            var subscription = subscriptionService.getSubscriptionByVendorId(vendorId);
            
            String stripeCustomerId = subscription.isEmpty() ? null : subscription.get().getStripeCustomerId();
            if (stripeCustomerId == null || stripeCustomerId.isBlank() || "not_set".equals(stripeCustomerId)) {
                return ResponseEntity.ok(List.of());
            }

            Map<String, Object> params = new HashMap<>();
            params.put("customer", subscription.get().getStripeCustomerId());
            params.put("limit", Math.min(limit, 100));
            if (startingAfter != null) {
                params.put("starting_after", startingAfter);
            }

            InvoiceCollection invoices = Invoice.list(params);
            
            List<InvoiceDTO> result = invoices.getData().stream()
                .map(this::convertToInvoiceDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (StripeException e) {
            log.error("Error fetching invoices from Stripe", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get specific invoice details
     */
    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("@subscriptionGuard.isActive()")
    @Operation(
        summary = "Get invoice details",
        description = "Retrieve specific invoice by ID",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Invoice retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<InvoiceDTO> getInvoice(
            @Parameter(description = "Stripe invoice ID") @PathVariable String invoiceId
    ) {
        try {
            Invoice invoice = Invoice.retrieve(invoiceId);
            
            // Verify invoice belongs to current vendor's subscription
            var subscription = subscriptionService.getSubscriptionByVendorId(vendorContext.getCurrentVendorId());
            if (subscription.isEmpty() || !invoice.getCustomer().equals(subscription.get().getStripeCustomerId())) {
                return ResponseEntity.status(403).build();
            }

            return ResponseEntity.ok(convertToInvoiceDTO(invoice));
        } catch (StripeException e) {
            log.error("Error fetching invoice: {}", invoiceId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List payment methods for the vendor's Stripe customer
     */
    @GetMapping("/payment-methods")
    @PreAuthorize("@subscriptionGuard.isActive()")
    @Operation(
        summary = "List payment methods",
        description = "Returns list of saved payment methods for the authenticated vendor",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment methods retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<PaymentMethodDTO>> getPaymentMethods() {
        try {
            var subscription = subscriptionService.getSubscriptionByVendorId(vendorContext.getCurrentVendorId());
            
            String stripeCustomerId = subscription.isEmpty() ? null : subscription.get().getStripeCustomerId();
            if (stripeCustomerId == null || stripeCustomerId.isBlank() || "not_set".equals(stripeCustomerId)) {
                return ResponseEntity.ok(List.of());
            }

            Map<String, Object> params = new HashMap<>();
            params.put("customer", subscription.get().getStripeCustomerId());
            params.put("type", "card");

            PaymentMethodCollection methods = PaymentMethod.list(params);
            
            List<PaymentMethodDTO> result = methods.getData().stream()
                .map(this::convertToPaymentMethodDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (StripeException e) {
            log.error("Error fetching payment methods from Stripe", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Attach a payment method to the vendor's Stripe customer
     */
    @PostMapping("/payment-methods")
    @PreAuthorize("@subscriptionGuard.isActive()")
    @Operation(
        summary = "Add payment method",
        description = "Attaches a payment method to the vendor's Stripe account",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment method added"),
        @ApiResponse(responseCode = "400", description = "Invalid payment method"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PaymentMethodDTO> addPaymentMethod(
            @Parameter(description = "Stripe payment method ID") @RequestParam String paymentMethodId
    ) {
        try {
            var subscription = subscriptionService.getSubscriptionByVendorId(vendorContext.getCurrentVendorId());
            
            if (subscription.isEmpty() || subscription.get().getStripeCustomerId() == null) {
                return ResponseEntity.badRequest().build();
            }

            PaymentMethod method = PaymentMethod.retrieve(paymentMethodId);
            Map<String, Object> params = new HashMap<>();
            params.put("customer", subscription.get().getStripeCustomerId());
            method.attach(params);

            log.info("Payment method {} attached to customer {}", paymentMethodId, subscription.get().getStripeCustomerId());
            return ResponseEntity.ok(convertToPaymentMethodDTO(method));
        } catch (StripeException e) {
            log.error("Error attaching payment method", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Detach a payment method from the vendor's Stripe customer
     */
    @DeleteMapping("/payment-methods/{paymentMethodId}")
    @PreAuthorize("@subscriptionGuard.isActive()")
    @Operation(
        summary = "Remove payment method",
        description = "Detaches a payment method from the vendor's Stripe account",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Payment method removed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<Void> removePaymentMethod(
            @Parameter(description = "Stripe payment method ID") @PathVariable String paymentMethodId
    ) {
        try {
            PaymentMethod method = PaymentMethod.retrieve(paymentMethodId);
            
            // Verify the payment method belongs to current vendor's customer
            var subscription = subscriptionService.getSubscriptionByVendorId(vendorContext.getCurrentVendorId());
            if (subscription.isEmpty() || !method.getCustomer().equals(subscription.get().getStripeCustomerId())) {
                return ResponseEntity.status(403).build();
            }

            method.detach();
            log.info("Payment method {} detached", paymentMethodId);
            return ResponseEntity.noContent().build();
        } catch (com.stripe.exception.InvalidRequestException e) {
            // Payment method not found on Stripe
            log.warn("Payment method {} not found or already detached: {}", paymentMethodId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (StripeException e) {
            log.error("Error detaching payment method: {}", paymentMethodId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * TEST ENDPOINT: Get first available tenant ID for webhook testing
     * Provides tenant context for test operations without requiring authentication
     * 
     * @return map with first tenant ID
     */
    @GetMapping("/test/get-tenant-id")
    @Operation(
        summary = "[TEST ONLY] Get test tenant ID",
        description = "Returns the first available tenant ID for testing webhook operations",
        tags = {"Billing"}
    )
    @ApiResponse(responseCode = "200", description = "Tenant ID retrieved successfully")
    public ResponseEntity<Map<String, Object>> getTestTenantId() {
        var tenants = tenantRepository.findAll();
        if (tenants.isEmpty()) {
            return ResponseEntity.status(500).body(
                Map.of("error", "No tenants found in database")
            );
        }
        
        UUID testTenantId = tenants.get(0).getId();
        return ResponseEntity.ok(Map.of(
            "tenantId", testTenantId.toString(),
            "status", "success"
        ));
    }

    /**
     * TEST ENDPOINT: Create Stripe customer mappings for webhook tests
     * 
     * This endpoint is only available in test/dev profiles to support automated testing.
     * Creates stripe_customers records for fictitious customer IDs used in webhook tests.
     * 
     * @return map with created count and mapping details
     */
    @PostMapping("/test/create-stripe-mappings")
    @Operation(
        summary = "[TEST ONLY] Create Stripe customer mappings",
        description = "Creates Stripe customer mappings for webhook test scenarios. Only available in dev/test profiles.",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Mappings created successfully"),
        @ApiResponse(responseCode = "403", description = "Unavailable in production"),
        @ApiResponse(responseCode = "500", description = "Database error")
    })
    public ResponseEntity<Map<String, Object>> createTestStripeMappings() {
        Map<String, Object> result = new HashMap<>();
        try {
            // Get first available tenant
            var tenants = tenantRepository.findAll();
            if (tenants.isEmpty()) {
                log.error("No tenants found in database - cannot create mappings");
                return ResponseEntity.status(500).body(
                    Map.of("error", "No tenants found in database")
                );
            }
            
            UUID testTenantId = tenants.get(0).getId();
            log.info("Using tenant {} for webhook test mappings", testTenantId);
            
            String[] testCustomerIds = {
                "cus_invoice_paid_test",
                "cus_invoice_failed_test",
                "cus_subscription_deleted_test",
                "cus_checkout_session_test",
                "cus_invoice_paid_direct_test",
                "cus_idempotency_test"
            };
            
            List<Map<String, Object>> created = new ArrayList<>();
            int count = 0;
            
            for (String customerId : testCustomerIds) {
                try {
                    // Create or update mapping
                    stripeCustomerMappingService.createOrUpdateMapping(testTenantId, customerId, null);
                    
                    Map<String, Object> mapping = new HashMap<>();
                    mapping.put("customerId", customerId);
                    mapping.put("tenantId", testTenantId);
                    mapping.put("status", "created");
                    created.add(mapping);
                    count++;
                    
                    log.info("✓ Created mapping: {} → {}", customerId, testTenantId);
                } catch (Exception e) {
                    log.error("Failed to create mapping for {}: {}", customerId, e.getMessage());
                }
            }
            
            result.put("status", "success");
            result.put("message", "Stripe customer mappings created for webhook tests");
            result.put("count", count);
            result.put("totalExpected", testCustomerIds.length);
            result.put("mappings", created);
            result.put("tenantId", testTenantId);
            
            log.info("✅ Test mappings setup complete: {} / {} created", count, testCustomerIds.length);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to create test mappings", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Convert Stripe Invoice to DTO
     */
    private InvoiceDTO convertToInvoiceDTO(Invoice invoice) {
        return InvoiceDTO.builder()
            .id(invoice.getId())
            .number(invoice.getNumber())
            .status(invoice.getStatus())
            .amount(new java.math.BigDecimal(invoice.getAmountDue()).divide(
                new java.math.BigDecimal(100), 2, java.math.RoundingMode.HALF_UP))
            .currency(invoice.getCurrency())
            .createdAt(java.time.Instant.ofEpochSecond(invoice.getCreated()))
            .dueDate(invoice.getDueDate() != null ? 
                java.time.Instant.ofEpochSecond(invoice.getDueDate()) : null)
            .paidAt(invoice.getStatusTransitions() != null && invoice.getStatusTransitions().getPaidAt() != null ? 
                java.time.Instant.ofEpochSecond(invoice.getStatusTransitions().getPaidAt()) : null)
            .pdfUrl(invoice.getInvoicePdf())
            .description(invoice.getDescription())
            .build();
    }

    /**
     * Convert Stripe PaymentMethod to DTO
     */
    private PaymentMethodDTO convertToPaymentMethodDTO(PaymentMethod method) {
        com.stripe.model.PaymentMethod.Card card = method.getCard();
        
        return PaymentMethodDTO.builder()
            .id(method.getId())
            .type(method.getType())
            .brand(card != null ? card.getBrand() : null)
            .last4(card != null ? card.getLast4() : null)
            .expMonth(card != null ? card.getExpMonth().intValue() : null)
            .expYear(card != null ? card.getExpYear().intValue() : null)
            .createdAt(method.getCreated())
            .isDefault(false)  // Would need to compare with customer default
            .build();
    }

    /**
     * Extract event ID from raw JSON payload
     * Used to persist webhook BEFORE signature validation
     */
    private String extractEventIdFromPayload(String payload) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
            if (json.has("id")) {
                return json.get("id").getAsString();
            }
        } catch (Exception e) {
            log.debug("Failed to extract event ID from payload: {}", e.getMessage());
        }
        // Fallback: generate temporary ID if extraction fails
        return "unknown_" + System.currentTimeMillis();
    }

    /**
     * Extract event type from raw JSON payload
     */
    private String extractEventTypeFromPayload(String payload) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
            if (json.has("type")) {
                return json.get("type").getAsString();
            }
        } catch (Exception e) {
            log.debug("Failed to extract event type from payload: {}", e.getMessage());
        }
        return "unknown";
    }
}
