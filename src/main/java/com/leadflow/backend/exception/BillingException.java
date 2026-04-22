package com.leadflow.backend.exception;

/**
 * BillingException — Thrown when subscription/billing check fails
 *
 * Common scenarios:
 * - User tried to access paid feature without active subscription
 * - Subscription expired or cancelled
 * - Tenant has no subscription
 * - Payment setup incomplete
 *
 * HTTP: 402 Payment Required (semantically correct for billing issues)
 * or 403 Forbidden (if billing is critical gate)
 */
public class BillingException extends RuntimeException {

    public BillingException(String message) {
        super(message);
    }

    public BillingException(String message, Throwable cause) {
        super(message, cause);
    }

    public BillingException(Throwable cause) {
        super(cause);
    }
}
