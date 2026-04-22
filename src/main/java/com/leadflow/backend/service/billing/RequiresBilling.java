package com.leadflow.backend.service.billing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @RequiresBilling — Marker for endpoints/methods requiring active subscription
 *
 * When placed on a controller method or service method:
 * - BillingAccessAspect intercepts the call
 * - Validates tenant has ACTIVE subscription
 * - If not → throws BillingException (403 Forbidden)
 * - If yes → method executes normally
 *
 * Usage Example:
 *
 * ```java
 * @RestController
 * @RequestMapping("/api/leads")
 * public class LeadController {
 *
 *     // Public, no billing required
 *     @GetMapping("/{id}")
 *     public ResponseEntity<Lead> getLead(@PathVariable UUID id) { ... }
 *
 *     // PAID feature — subscription required
 *     @PostMapping
 *     @RequiresBilling
 *     public ResponseEntity<Lead> createLead(@RequestBody CreateLeadRequest req) { ... }
 *
 *     // PAID feature — subscription required
 *     @DeleteMapping("/{id}")
 *     @RequiresBilling
 *     public ResponseEntity<?> deleteLead(@PathVariable UUID id) { ... }
 * }
 * ```
 *
 * Features Protected by @RequiresBilling:
 * - AI features (chat, summary, classify, etc.)
 * - Lead management (create, update, delete)
 * - Advanced integrations
 * - Export/reporting
 *
 * Free operations (no @RequiresBilling):
 * - Auth (register, login, logout)
 * - Profile viewing
 * - Account settings
 * - Public API docs
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresBilling {
    // Marker annotation — no parameters needed
    // BillingAccessAspect will intercept all methods marked with this
}
