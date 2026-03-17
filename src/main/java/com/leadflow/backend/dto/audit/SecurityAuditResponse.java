package com.leadflow.backend.dto.audit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.leadflow.backend.entities.audit.SecurityAction;

import java.time.LocalDateTime;
import java.util.UUID;

public record SecurityAuditResponse(
        UUID id,
        SecurityAction action,
        String email,
        String tenant,
        boolean success,
        String ipAddress,
        String userAgent,
        String correlationId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime createdAt
) {}