package com.leadflow.backend.repository.specification;

import com.leadflow.backend.entities.StripeEventLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;

/**
 * QueryDSL Specification for filtering Stripe webhook events with flexible criteria.
 * Enables complex queries for dashboard searches, filtering, and aggregations.
 */
@Component
@RequiredArgsConstructor
public class WebhookSpecification {

    /**
     * Filter by tenant ID - isolates events to specific tenant
     */
    public Specification<StripeEventLog> byTenant(UUID tenantId) {
        return (root, query, cb) -> tenantId != null
                ? cb.equal(root.get("tenantId"), tenantId)
                : null;
    }

    /**
     * Filter by event status (SUCCESS, FAILED, RETRY_PENDING, etc.)
     */
    public Specification<StripeEventLog> byStatus(StripeEventLog.EventProcessingStatus status) {
        return (root, query, cb) -> status != null
                ? cb.equal(root.get("status"), status)
                : null;
    }

    /**
     * Filter by event status list (OR condition)
     */
    public Specification<StripeEventLog> byStatusIn(List<StripeEventLog.EventProcessingStatus> statuses) {
        return (root, query, cb) -> statuses != null && !statuses.isEmpty()
                ? root.get("status").in(statuses)
                : null;
    }

    /**
     * Filter by event type (e.g., "invoice.paid", "customer.subscription.updated")
     */
    public Specification<StripeEventLog> byEventType(String eventType) {
        return (root, query, cb) -> eventType != null
                ? cb.like(cb.lower(root.get("eventType")), "%" + eventType.toLowerCase() + "%")
                : null;
    }

    /**
     * Filter by customer ID (Stripe customer)
     */
    public Specification<StripeEventLog> byCustomerId(String customerId) {
        return (root, query, cb) -> customerId != null
                ? cb.equal(root.get("customerId"), customerId)
                : null;
    }

    /**
     * Filter by event ID (idempotency key)
     */
    public Specification<StripeEventLog> byEventId(String eventId) {
        return (root, query, cb) -> eventId != null
                ? cb.equal(root.get("eventId"), eventId)
                : null;
    }

    /**
     * Filter by creation date range
     */
    public Specification<StripeEventLog> byCreatedDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return predicates.isEmpty()
                    ? null
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by retry count greater than or equal to threshold
     */
    public Specification<StripeEventLog> byMinRetryCount(Integer minRetries) {
        return (root, query, cb) -> minRetries != null
                ? cb.greaterThanOrEqualTo(root.get("retryCount"), minRetries)
                : null;
    }

    /**
     * Filter events that have errors (not null)
     */
    public Specification<StripeEventLog> withErrors() {
        return (root, query, cb) -> cb.isNotNull(root.get("lastError"));
    }

    /**
     * Filter by error message containing text
     */
    public Specification<StripeEventLog> byErrorContains(String errorText) {
        return (root, query, cb) -> errorText != null
                ? cb.like(
                        cb.lower(root.get("lastError")),
                        "%" + errorText.toLowerCase() + "%"
                )
                : null;
    }

    /**
     * Combine multiple specifications with AND logic
     */
    public Specification<StripeEventLog> and(
            Specification<StripeEventLog> spec1,
            Specification<StripeEventLog> spec2) {
        
        return spec1 != null && spec2 != null ? spec1.and(spec2) : spec1 != null ? spec1 : spec2;
    }

    /**
     * Combine multiple specifications with AND logic (variadic)
     */
    @SuppressWarnings("varargs")
    public Specification<StripeEventLog> andAll(Specification<StripeEventLog>... specs) {
        Specification<StripeEventLog> result = null;

        for (Specification<StripeEventLog> spec : specs) {
            if (spec != null) {
                result = result == null ? spec : result.and(spec);
            }
        }

        return result;
    }

    /**
     * Build a filter for pending webhooks (not successfully processed)
     */
    public Specification<StripeEventLog> pendingWebhooks() {
        List<StripeEventLog.EventProcessingStatus> pendingStatuses = List.of(
                StripeEventLog.EventProcessingStatus.PENDING,
                StripeEventLog.EventProcessingStatus.RETRY_PENDING
        );
        return byStatusIn(pendingStatuses);
    }

    /**
     * Build a filter for failed webhooks
     */
    public Specification<StripeEventLog> failedWebhooks() {
        return byStatus(StripeEventLog.EventProcessingStatus.FAILED);
    }

    /**
     * Build a filter for successful webhooks
     */
    public Specification<StripeEventLog> successfulWebhooks() {
        return byStatus(StripeEventLog.EventProcessingStatus.SUCCESS);
    }

    /**
     * Build comprehensive filter for dashboard search
     */
    public Specification<StripeEventLog> dashboardSearch(
            UUID tenantId,
            String eventType,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String searchTerm) {

        List<Specification<StripeEventLog>> specs = new ArrayList<>();

        if (tenantId != null) {
            specs.add(byTenant(tenantId));
        }

        if (eventType != null && !eventType.isEmpty()) {
            specs.add(byEventType(eventType));
        }

        if (status != null && !status.isEmpty()) {
            try {
                specs.add(byStatus(StripeEventLog.EventProcessingStatus.valueOf(status.toUpperCase())));
            } catch (IllegalArgumentException e) {
                // Invalid status, skip
            }
        }

        if (startDate != null || endDate != null) {
            specs.add(byCreatedDateRange(startDate, endDate));
        }

        if (searchTerm != null && !searchTerm.isEmpty()) {
            specs.add(byEventId(searchTerm));
        }

        return specs.isEmpty() ? null : andAll(specs.toArray(new Specification[0]));
    }
}
