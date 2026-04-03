package com.leadflow.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        @JsonProperty("email")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        @JsonProperty("password")
        String password,

        @NotNull(message = "Tenant ID é obrigatório")
        @NotBlank(message = "Tenant ID não pode estar vazio")
        @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", 
                 message = "Tenant ID deve ser um UUID válido")
        @JsonProperty("tenantId")
        String tenantId

) {

    @JsonCreator
    public LoginRequest {
        // Normalização defensiva
        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }
}