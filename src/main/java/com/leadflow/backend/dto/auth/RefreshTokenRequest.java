package com.leadflow.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token é obrigatório")
        @JsonProperty("refreshToken")
        String refreshToken
) {}
