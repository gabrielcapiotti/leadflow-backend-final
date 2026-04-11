package com.leadflow.backend.dto.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record SecurityAuditResponse(
        UUID id,
        String eventCategory,
        String action,
        String actorEmail,
        UUID tenantId,
        String entityType,
        UUID entityId,
        Boolean success,
        String ipAddress,
        String userAgent,
        String correlationId,
        JsonNode details,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant createdAt
) {}