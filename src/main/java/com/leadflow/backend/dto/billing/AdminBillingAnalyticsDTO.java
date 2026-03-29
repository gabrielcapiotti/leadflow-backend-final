package com.leadflow.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for admin billing analytics dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBillingAnalyticsDTO {
    private Long totalSubscriptions;
    private Long activeSubscriptions;
    private Long trialingSubscriptions;
    private Long pastDueSubscriptions;
    private Long cancelledSubscriptions;
    private Double activeSubscriptionPercentage;
    private Double churnRate;
    private Long totalCustomers;
    private Long newCustomersThisMonth;
    private BigDecimal monthlyRecurringRevenue;
    private BigDecimal annualRecurringRevenue;
    private BigDecimal averageMonthlyValue;
    private Long failedPaymentsCount;
    private Long retriedPaymentsCount;
    private Long successfulPaymentsCount;
    private Double paymentSuccessRate;
    private Map<String, Long> subscriptionsByStatus;
    private List<DailyMetricPoint> dailyActiveSubscriptions;
    private List<DailyMetricPoint> dailyNewSubscriptions;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyMetricPoint {
        private String date;
        private Long value;
    }
}
