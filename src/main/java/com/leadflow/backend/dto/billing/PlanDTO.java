package com.leadflow.backend.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.leadflow.backend.entities.Plan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanDTO {
    
    @JsonProperty("id")
    private UUID id;
    
    @JsonProperty("code")
    private String code;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("max_leads")
    private Integer maxLeads;
    
    @JsonProperty("max_users")
    private Integer maxUsers;
    
    @JsonProperty("max_ai_executions")
    private Integer maxAiExecutions;
    
    @JsonProperty("active")
    private Boolean active;

    /**
     * Convert Plan entity to DTO
     */
    public static PlanDTO fromEntity(Plan plan) {
        if (plan == null) {
            return null;
        }
        
        return PlanDTO.builder()
            .id(plan.getId())
            .code(plan.getCode())
            .name(plan.getName())
            .maxLeads(plan.getMaxLeads())
            .maxUsers(plan.getMaxUsers())
            .maxAiExecutions(plan.getMaxAiExecutions())
            .active(plan.getActive())
            .build();
    }
}
