package com.leadflow.backend.dto.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

/**
 * DTO para atualização parcial (PATCH) de Settings.
 * Todos os campos sáo opcionais.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatchSettingRequest {

    @Size(max = 100, message = "Vendor name deve ter no máximo 100 caracteres")
    private String vendorName;

    @Size(max = 15, message = "WhatsApp deve ter no máximo 15 caracteres")
    private String whatsapp;

    @Size(max = 100, message = "Company name deve ter no máximo 100 caracteres")
    private String companyName;

    private String logo;

    @Size(max = 500, message = "Welcome message deve ter no máximo 500 caracteres")
    private String welcomeMessage;

    public PatchSettingRequest() {}

    public PatchSettingRequest(
            String vendorName,
            String whatsapp,
            String companyName,
            String logo,
            String welcomeMessage
    ) {
        this.vendorName = vendorName;
        this.whatsapp = whatsapp;
        this.companyName = companyName;
        this.logo = logo;
        this.welcomeMessage = welcomeMessage;
    }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getWelcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage; }
}
