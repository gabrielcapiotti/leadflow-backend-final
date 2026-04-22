package com.leadflow.backend.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for admin billing revenue reports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBillingRevenueDTO {
    private BigDecimal totalRevenueAllTime;
    private BigDecimal totalRevenueThisMonth;
    private BigDecimal totalRevenueThisYear;
    @JsonProperty("averageMonthlyValue")
    private BigDecimal averageRevenuePerSubscription;
    private BigDecimal monthlyRecurringRevenue;
    private BigDecimal annualRecurringRevenue;
    private Long totalPaymentSuccesses;
    private Long totalPaymentFailures;
    private BigDecimal totalRefundsIssued;
    private Long totalRefundCount;
    private BigDecimal netRevenue;
    private Double revenueGrowthPercentage;
    private List<MonthlyRevenuePoint> monthlyRevenue;
    private List<TopPlanRevenue> topPlans;
    private List<RevenueBreakdown> revenueByStatus;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyRevenuePoint {
        private String month;
        private BigDecimal amount;
        private Long transactionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopPlanRevenue {
        private String planName;
        private BigDecimal totalRevenue;
        private Long subscriptionCount;
        private Double percentageOfTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueBreakdown {
        private String status;
        private BigDecimal amount;
        private Long count;
        private Double percentageOfTotal;
    }
}
