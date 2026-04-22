package com.leadflow.backend.specification;

import com.leadflow.backend.entities.audit.SecurityAuditLog;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SecurityAuditSpecification {

    private SecurityAuditSpecification() {
        // Utility class
    }

    public static Specification<SecurityAuditLog> filter(
            String actorEmail,
            UUID tenantId,
            String action,
            Boolean success,
            Instant from,
            Instant to
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (actorEmail != null && !actorEmail.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("actorEmail")),
                                "%" + actorEmail.trim().toLowerCase() + "%"
                        )
                );
            }

            if (tenantId != null) {
                predicates.add(
                        cb.equal(
                                root.get("tenantId"),
                                tenantId
                        )
                );
            }

            if (action != null && !action.isBlank()) {
                predicates.add(
                        cb.equal(root.get("action"), action)
                );
            }

            if (success != null) {
                predicates.add(
                        cb.equal(root.get("success"), success)
                );
            }

            if (from != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from)
                );
            }

            if (to != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("createdAt"), to)
                );
            }

            // Ordenação padrão por data desc se não houver sort explícito
            if (query != null && query.getOrderList().isEmpty()) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}