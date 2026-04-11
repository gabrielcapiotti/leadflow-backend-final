package com.leadflow.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class ChatRequest {

    @NotNull(message = "vendorLeadId é obrigatório")
    private UUID vendorLeadId;

    @NotBlank(message = "message é obrigatória")
    @Size(max = 2000, message = "message deve ter no máximo 2000 caracteres")
    private String message;

    public UUID getVendorLeadId() {
        return vendorLeadId;
    }

    public void setVendorLeadId(UUID vendorLeadId) {
        this.vendorLeadId = vendorLeadId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}