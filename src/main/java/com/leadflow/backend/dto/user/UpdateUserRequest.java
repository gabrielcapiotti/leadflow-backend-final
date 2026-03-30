package com.leadflow.backend.dto.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UpdateUserRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    private final String name;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 100, message = "Email deve ter no máximo 100 caracteres")
    private final String email;

    // roleId is optional - only ADMIN users need to provide it
    // Normal users cannot update their own role through this endpoint
    private final UUID roleId;

    @JsonCreator
    public UpdateUserRequest(
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("roleId") UUID roleId
    ) {
        this.name = name;
        this.email = email;
        this.roleId = roleId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public UUID getRoleId() {
        return roleId;
    }
}
