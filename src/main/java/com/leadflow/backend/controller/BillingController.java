package com.leadflow.backend.controller;

import com.leadflow.backend.config.metrics.WebhookMetrics;
import com.leadflow.backend.dto.billing.CheckoutRequest;
import com.leadflow.backend.dto.billing.CheckoutResponse;
import com.leadflow.backend.dto.billing.InvoiceDTO;
import com.leadflow.backend.dto.billing.PaymentMethodDTO;
import com.leadflow.backend.dto.billing.SubscriptionDetailsDTO;
import com.leadflow.backend.exception.StripeSignatureVerificationException;
import com.leadflow.backend.exception.StripeTimestampExpiredException;
import com.leadflow.backend.security.VendorContext;
import com.leadflow.backend.service.billing.StripeService;
import com.leadflow.backend.service.billing.StripeWebhookValidator;
import com.leadflow.backend.service.billing.StripeWebhookAlertService;
import com.leadflow.backend.service.vendor.SubscriptionService;
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
import org.springframework.web.bind.annotation.*;

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
        
        try {
            // Parse Stripe-Signature header format: "t=<timestamp>,v1=<signature>"
            if (stripeSignature == null || stripeSignature.isBlank()) {
                log.warn("Missing Stripe-Signature header");
                webhookMetrics.recordEventType("invalid");
                webhookMetrics.incrementFailureCounter("invalid", "missing_signature");
                webhookAlertService.recordFailure("invalid", "Missing Stripe-Signature header", null);
                return ResponseEntity.badRequest().body("Missing Stripe-Signature header");
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
                return ResponseEntity.badRequest().body("Webhook timestamp too old");
            }

            // Validate signature
            try {
                webhookValidator.validateSignature(payload, signature, timestamp);
                webhookMetrics.recordSignatureValidation(true);
            } catch (StripeSignatureVerificationException e) {
                webhookMetrics.recordSignatureValidation(false);
                webhookMetrics.incrementFailureCounter("all", "invalid_signature");
                webhookAlertService.recordFailure("all", "Webhook signature verification failed: " + e.getMessage(), e);
                log.warn("Webhook signature verification failed: {}", e.getMessage());
                return ResponseEntity.badRequest().body("Invalid webhook signature");
            }

            // Process webhook event and route to appropriate handler
            eventType = stripeService.processWebhookEvent(payload);
            webhookMetrics.recordEventType(eventType);
            webhookMetrics.incrementSuccessCounter(eventType);
            webhookAlertService.recordSuccess(eventType);

            long duration = System.currentTimeMillis() - startTime;
            webhookMetrics.recordProcessingDelay(duration);

            log.info("Webhook processed successfully: event_type={}, duration={}ms", eventType, duration);
            return ResponseEntity.ok("Webhook processed");

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
     * Get vendor's current subscription details
     * 
     * Returns 204 No Content if:
     * - No vendor context (user not properly linked)
     * - No subscription found for vendor
     */
    @GetMapping("/subscription")
    @Operation(
        summary = "Get subscription details",
        description = "Returns the current subscription status and details for the authenticated vendor",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Subscription details retrieved",
            content = @Content(schema = @Schema(implementation = SubscriptionDetailsDTO.class))),
        @ApiResponse(responseCode = "204", description = "No subscription found for user"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<SubscriptionDetailsDTO> getSubscriptionDetails() {
        UUID vendorId;

        try {
            vendorId = vendorContext.getCurrentVendorId();
        } catch (Exception e) {
            log.warn("Vendor context resolution failed: {}", e.getMessage());
            return ResponseEntity.noContent().build();
        }

        if (vendorId == null) {
            return ResponseEntity.noContent().build();
        }

        var subscription = subscriptionService.getSubscriptionByVendorId(vendorId);

        if (subscription.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(
                SubscriptionDetailsDTO.fromEntity(subscription.get())
        );
    }

    /**
     * Get vendor's invoices
     */
    @GetMapping("/invoices")
    @PreAuthorize("@subscriptionGuard.isActive()")
    @Operation(
        summary = "List invoices",
        description = "Returns paginated list of invoices for the authenticated vendor",
        tags = {"Billing"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Invoices retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<InvoiceDTO>> getInvoices(
            @Parameter(description = "Limit per page") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Starting after invoice ID") @RequestParam(required = false) String startingAfter
    ) {
        try {
            var subscription = subscriptionService.getSubscriptionByVendorId(vendorContext.getCurrentVendorId());
            
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
        } catch (StripeException e) {
            log.error("Error detaching payment method: {}", paymentMethodId, e);
            return ResponseEntity.notFound().build();
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
}
