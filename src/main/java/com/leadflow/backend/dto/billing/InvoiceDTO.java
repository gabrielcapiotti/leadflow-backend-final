package com.leadflow.backend.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDTO {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("number")
    private String number;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("due_date")
    private Instant dueDate;
    
    @JsonProperty("paid_at")
    private Instant paidAt;
    
    @JsonProperty("pdf_url")
    private String pdfUrl;
    
    @JsonProperty("description")
    private String description;
}
