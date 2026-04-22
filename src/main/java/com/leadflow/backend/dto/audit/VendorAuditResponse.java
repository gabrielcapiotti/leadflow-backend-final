package com.leadflow.backend.dto.audit;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record VendorAuditResponse(
        UUID id,
        UUID vendorId,
        String userEmail,
        String acao,
        String entityType,
        UUID entidadeId,
        String detalhes,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant createdAt
) {}