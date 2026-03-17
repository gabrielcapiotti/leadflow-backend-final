package com.leadflow.backend.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodDTO {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private String type; // "card" or "bank_account"
    
    @JsonProperty("brand")
    private String brand; // "visa", "mastercard", etc
    
    @JsonProperty("last4")
    private String last4;
    
    @JsonProperty("exp_month")
    private Integer expMonth;
    
    @JsonProperty("exp_year")
    private Integer expYear;
    
    @JsonProperty("is_default")
    private Boolean isDefault;
    
    @JsonProperty("created_at")
    private Long createdAt;
}
