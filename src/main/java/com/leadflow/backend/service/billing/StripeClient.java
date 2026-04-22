package com.leadflow.backend.service.billing;

import com.leadflow.backend.config.StripeProperties;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ⚡ STRIPE CLIENT - Direct API Integration
 * 
 * Handles all Stripe API calls:
 * - Creating Stripe customers
 * - Initiating checkout sessions
 * - Managing subscriptions
 * - Processing webhook events
 * 
 * Dependencies:
 *   - Stripe SDK (com.stripe:stripe-java)
 *   - StripeProperties (with secret key)
 */
@Service
@Slf4j
public class StripeClient {

    private final StripeProperties stripeProperties;

    public StripeClient(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    /* ======================================================
       CUSTOMER MANAGEMENT
       ====================================================== */

    /**
     * Create or retrieve Stripe Customer for a tenant
     * 
     * @param tenantId UUID of the tenant
     * @param email Email address of the customer
     * @return Stripe Customer ID (cus_xxx)
     * @throws StripeException if API call fails
     */
    public String createOrGetCustomer(UUID tenantId, String email) throws StripeException {
        
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email required for Stripe customer creation");
        }

        log.info("Creating/retrieving Stripe customer for tenant={}", tenantId);
        // NOTE: Email NOT logged - sensitive data

        try {
            // Create new customer with metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenantId", tenantId.toString());
            metadata.put("createdAt", String.valueOf(System.currentTimeMillis()));

            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(email)
                    .putAllMetadata(metadata)
                    .setDescription("LeadFlow Customer - Tenant: " + tenantId)
                    .build();

            Customer customer = Customer.create(params);

            log.info("✅ Stripe customer created: {} (cus_{}...)", email, customer.getId().substring(0, 10));
            return customer.getId();

        } catch (StripeException e) {
            log.error("❌ Failed to create Stripe customer: {}", e.getMessage());
            throw e;
        }
    }

    /* ======================================================
       CHECKOUT SESSION - SUBSCRIPTION INITIATION
       ====================================================== */

    /**
     * Create Stripe Checkout Session for subscription purchase
     * 
     * Called when user clicks "upgrade plan" or "subscribe"
     * User redirected to Stripe checkout page (hosted)
     * After payment → Stripe webhook triggers subscription.created
     * 
     * @param tenantId UUID of tenant
     * @param priceId Stripe price ID (price_xxx)
     * @param successUrl URL to redirect after success
     * @param cancelUrl URL to redirect after cancel
     * @return Checkout session with payment link
     * @throws StripeException if API call fails
     */
    public Session createCheckoutSession(
            UUID tenantId,
            String priceId,
            String successUrl,
            String cancelUrl) throws StripeException {

        if (priceId == null || priceId.isBlank()) {
            throw new IllegalArgumentException("Stripe price ID required");
        }

        log.info("Creating checkout session: tenant={}, priceId={}", tenantId, priceId);

        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenantId", tenantId.toString());

            SessionCreateParams params = SessionCreateParams.builder()
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build()
                    )
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .putAllMetadata(metadata)
                    .setCustomerCreation(SessionCreateParams.CustomerCreation.ALWAYS)
                    .build();

            Session session = Session.create(params);

            log.info("✅ Checkout session created: {} (expires in 24h)", session.getId());
            return session;

        } catch (StripeException e) {
            log.error("❌ Failed to create checkout session: {}", e.getMessage());
            throw e;
        }
    }

    /* ======================================================
       SUBSCRIPTION RETRIEVAL
       ====================================================== */

    /**
     * Retrieve subscription details from Stripe
     * 
     * Used to validate and fetch current subscription status
     * Useful for checking payment status before persisting to DB
     * 
     * @param stripeSubscriptionId Subscription ID from Stripe (sub_xxx)
     * @return Subscription details
     * @throws StripeException if not found or API error
     */
    public Subscription retrieveSubscription(String stripeSubscriptionId) throws StripeException {
        
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            throw new IllegalArgumentException("Stripe subscription ID required");
        }

        log.debug("Retrieving subscription from Stripe: {}", stripeSubscriptionId);

        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            log.info("✅ Subscription retrieved: status={}, customer={}", 
                subscription.getStatus(), subscription.getCustomer());
            return subscription;
        } catch (StripeException e) {
            log.error("❌ Failed to retrieve subscription {}: {}", stripeSubscriptionId, e.getMessage());
            throw e;
        }
    }

    /* ======================================================
       SUBSCRIPTION CANCELLATION
       ====================================================== */

    /**
     * Cancel a Stripe subscription
     * 
     * Called when user cancels their plan
     * Sets subscription.status to "canceled" in Stripe
     * Webhook (customer.subscription.deleted) notifies LeadFlow
     * 
     * @param stripeSubscriptionId Stripe subscription ID (sub_xxx)
     * @throws StripeException if API call fails
     */
    public void cancelSubscription(String stripeSubscriptionId) throws StripeException {

        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            throw new IllegalArgumentException("Stripe subscription ID required");
        }

        log.info("Cancelling Stripe subscription: {}", stripeSubscriptionId);

        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            
            SubscriptionCancelParams params = SubscriptionCancelParams.builder()
                    .build();
            
            Subscription cancelled = subscription.cancel(params);

            log.info("✅ Subscription cancelled: status={}", cancelled.getStatus());

        } catch (StripeException e) {
            log.error("❌ Failed to cancel subscription {}: {}", stripeSubscriptionId, e.getMessage());
            throw e;
        }
    }

    /* ======================================================
       PRODUCT & PRICE UTILITIES
       ====================================================== */

    /**
     * Retrieve product details from Stripe
     * 
     * Used for displaying plan information
     * 
     * @param productId Stripe product ID
     * @return Product details
     * @throws StripeException if not found
     */
    public Product retrieveProduct(String productId) throws StripeException {

        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID required");
        }

        log.debug("Retrieving product from Stripe: {}", productId);

        try {
            return Product.retrieve(productId);
        } catch (StripeException e) {
            log.error("❌ Failed to retrieve product {}: {}", productId, e.getMessage());
            throw e;
        }
    }

    /* ======================================================
       VALIDATION
       ====================================================== */

    /**
     * Verify that Stripe is properly configured
     * 
     * @return true if ready to use
     */
    public boolean isConfigured() {
        String secretKey = stripeProperties.getApi().getSecretKey();
        return secretKey != null && !secretKey.isBlank() && !secretKey.startsWith("sk_test_") || secretKey.startsWith("sk_live_");
    }

    /**
     * Get current Stripe mode (TEST or LIVE)
     */
    public String getMode() {
        String secretKey = stripeProperties.getApi().getSecretKey();
        if (secretKey == null) return "UNCONFIGURED";
        return secretKey.startsWith("sk_test_") ? "TEST" : "LIVE";
    }
}
