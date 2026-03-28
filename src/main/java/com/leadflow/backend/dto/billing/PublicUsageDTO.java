package com.leadflow.backend.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public usage DTO for authenticated users
 * Used in test environment and public endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUsageDTO {
    
    @JsonProperty("leads_limit")
    private Integer leadsLimit;
    
    @JsonProperty("leads_used")
    private Integer leadsUsed;
    
    @JsonProperty("leads_remaining")
    private Integer leadsRemaining;
    
    @JsonProperty("conversations_limit")
    private Integer conversationsLimit;
    
    @JsonProperty("conversations_used")
    private Integer conversationsUsed;
    
    @JsonProperty("ai_executions_limit")
    private Integer aiExecutionsLimit;
    
    @JsonProperty("ai_executions_used")
    private Integer aiExecutionsUsed;
    
    @JsonProperty("usage_percentage")
    private Double usagePercentage;
    
    @JsonProperty("is_test_environment")
    private boolean isTestEnvironment;
    
    public static PublicUsageDTO testDefault() {
        return PublicUsageDTO.builder()
            .leadsLimit(100)
            .leadsUsed(0)
            .leadsRemaining(100)
            .conversationsLimit(1000)
            .conversationsUsed(0)
            .aiExecutionsLimit(500)
            .aiExecutionsUsed(0)
            .usagePercentage(0.0)
            .isTestEnvironment(true)
            .build();
    }
}
