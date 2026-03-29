package com.leadflow.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing a user with billing information for admin dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBillingUserDTO {
    private UUID tenantId;
    private String email;
    private String subscriptionStatus;
    private String planName;
    private BigDecimal monthlyValue;
    private LocalDateTime subscriptionStartDate;
    private LocalDateTime subscriptionEndDate;
    private LocalDateTime nextBillingDate;
    private Boolean isTrialing;
    private Integer trialDaysRemaining;
    private Boolean hasFailedPayment;
    private LocalDateTime lastPaymentDate;
}
