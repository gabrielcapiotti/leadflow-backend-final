package com.leadflow.backend.service.audit;

import com.leadflow.backend.entities.audit.SecurityAction;
import com.leadflow.backend.entities.audit.SecurityAuditLog;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.audit.SecurityAuditLogRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class SecurityAuditService {

    private static final Logger logger =
            LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditLogRepository repository;

    public SecurityAuditService(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void log(
            SecurityAction action,
            String email,
            UUID tenantId,
            boolean success,
            String ipAddress,
            String userAgent,
            String correlationId
    ) {

        Objects.requireNonNull(action, "Security action cannot be null");

        SecurityAuditLog log = new SecurityAuditLog(
                action.name(),
                safe(email),
                tenantId,
                "SECURITY",
                null,
                success,
                safe(ipAddress),
                safe(userAgent),
                safe(correlationId),
                null
        );

        repository.save(log);

        logger.debug(
                "Security audit logged: action={}, email={}, tenantId={}, success={}",
                action,
                email,
                tenantId,
                success
        );
    }

    private String safe(String value) {
        return value == null ? null : value.trim();
    }
}