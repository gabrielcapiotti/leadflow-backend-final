package com.leadflow.backend.service.billing;

import com.leadflow.backend.exception.BillingException;
import com.leadflow.backend.multitenancy.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * BillingAccessAspect — AOP interceptor for @RequiresBilling
 *
 * Enforces billing validation at method level
 * Works with any endpoint/service method marked @RequiresBilling
 *
 * Usage:
 * ```
 * @PostMapping("/leads")
 * @RequiresBilling
 * public ResponseEntity<Lead> createLead(...) {
 *     // BillingAccessValidator.validateActiveSubscription() runs FIRST
 *     // If subscription invalid → BillingException thrown
 *     // If valid → method executes
 * }
 * ```
 *
 * Implementation:
 * - Intercepts methods with @RequiresBilling annotation
 * - Extracts tenantId from TenantContext (JWT-based)
 * - Validates subscription via BillingAccessValidator
 * - Throws BillingException if validation fails
 * - Logs all validation outcomes
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class BillingAccessAspect {

    private final BillingAccessValidator billingValidator;

    /**
     * Intercept all methods marked with @RequiresBilling
     *
     * Execution order:
     * 1. Extract tenant from context
     * 2. Validate subscription is ACTIVE
     * 3. Proceed with method execution
     * 4. Catch BillingException and log
     */
    @Around("@annotation(com.leadflow.backend.service.billing.RequiresBilling)")
    public Object validateBillingAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId;

        try {
            tenantId = TenantContext.requireTenant();
        } catch (IllegalStateException e) {
            log.warn("❌ BillingAspect: TenantContext not set - cannot validate billing");
            throw new BillingException("Tenant context not available - authentication may have failed");
        }

        // CRITICAL: Validate subscription BEFORE executing method
        try {
            billingValidator.validateActiveSubscription(tenantId);
            log.debug("✅ BillingAspect: Access granted for tenant={}, method={}", 
                    tenantId, joinPoint.getSignature().getName());
        } catch (BillingException e) {
            log.warn("🚫 BillingAspect: Access DENIED for tenant={}, method={}, reason={}", 
                    tenantId, joinPoint.getSignature().getName(), e.getMessage());
            throw e;
        }

        // Proceed to actual method
        return joinPoint.proceed();
    }
}
