package com.leadflow.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthResponse(

        @JsonProperty("accessToken")
        String accessToken,

        @JsonProperty("refreshToken")
        String refreshToken,

        @JsonProperty("tenantId")
        String tenantId

) {

    public AuthResponse {

        if (accessToken == null) {
            throw new IllegalArgumentException("Access token cannot be null");
        }

        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh token cannot be null");
        }

        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        accessToken = accessToken.trim();
        refreshToken = refreshToken.trim();
        tenantId = tenantId.trim();

        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token cannot be blank");
        }

        if (refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token cannot be blank");
        }

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be blank");
        }
    }
}