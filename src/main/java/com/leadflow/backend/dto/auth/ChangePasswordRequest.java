package com.leadflow.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "Senha atual é obrigatória")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        @JsonProperty("currentPassword")
        String currentPassword,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        @JsonProperty("newPassword")
        String newPassword,

        @NotBlank(message = "Confirmação de senha é obrigatória")
        @JsonProperty("confirmPassword")
        String confirmPassword
) {}
