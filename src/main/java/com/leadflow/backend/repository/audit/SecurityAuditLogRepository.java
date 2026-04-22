package com.leadflow.backend.repository.audit;

import com.leadflow.backend.entities.audit.SecurityAuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface SecurityAuditLogRepository
        extends JpaRepository<SecurityAuditLog, UUID>,
                JpaSpecificationExecutor<SecurityAuditLog> {

    long deleteByCreatedAtBefore(Instant threshold);

    Page<SecurityAuditLog> findByActorEmailContainingIgnoreCase(
            String email,
            Pageable pageable
    );

    Page<SecurityAuditLog> findByAction(
            String action,
            Pageable pageable
    );

    Page<SecurityAuditLog> findByCreatedAtBetween(
            Instant from,
            Instant to,
            Pageable pageable
    );
}