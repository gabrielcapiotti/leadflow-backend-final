package com.leadflow.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
    @Email(message = "Email deve ser válido")
    @NotBlank(message = "Email é obrigatório")
    String email
) {}
