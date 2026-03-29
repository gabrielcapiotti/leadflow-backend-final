package com.leadflow.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response wrapper for admin users billing list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBillingUsersResponse {
    private Long totalCount;
    private Long activeCount;
    private Long trialingCount;
    private Long pastDueCount;
    private List<AdminBillingUserDTO> users;
    private LocalDateTime generatedAt;
}
