package com.leadflow.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for refund processing results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    private UUID refundId;
    private String userId;
    private BigDecimal amount;
    private String status;
    private String reason;
    private String transactionId;
    private LocalDateTime processedAt;
    private String processedBy;
    private String message;
}
