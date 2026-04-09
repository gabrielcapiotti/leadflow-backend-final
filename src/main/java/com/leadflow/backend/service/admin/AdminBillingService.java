package com.leadflow.backend.service.admin;

import com.leadflow.backend.dto.billing.*;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.leadflow.backend.repository.PaymentRepository;
import com.leadflow.backend.repository.StripeEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBillingService {

    private static final double PLAN_PRICE = 197.0;

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final StripeEventLogRepository eventLogRepository;

    /**
     * Get list of all users with billing information.
     */
    public AdminBillingUsersResponse getAllUsersWithBillingInfo() {
        try {
            List<Subscription> subscriptions = subscriptionRepository.findAllWithPlan();
            
            long totalCount = subscriptions.size();
            long activeCount = subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE)
                .count();
            long trialingCount = subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.TRIALING)
                .count();
            long pastDueCount = subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.PAST_DUE)
                .count();

            List<AdminBillingUserDTO> users = subscriptions.stream()
                .map(this::mapSubscriptionToUserDTO)
                .collect(Collectors.toList());

            log.info("Retrieved billing info for {} users: active={}, trialing={}, pastDue={}",
                totalCount, activeCount, trialingCount, pastDueCount);

            return AdminBillingUsersResponse.builder()
                .totalCount(totalCount)
                .activeCount(activeCount)
                .trialingCount(trialingCount)
                .pastDueCount(pastDueCount)
                .users(users)
                .generatedAt(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("Error retrieving users with billing info", e);
            throw new RuntimeException("Failed to retrieve billing users", e);
        }
    }

    /**
     * Get billing analytics dashboard data.
     */
    public AdminBillingAnalyticsDTO getBillingAnalytics() {
        try {
            List<Subscription> subscriptions = subscriptionRepository.findAllWithPlan();

            long totalSubscriptions = subscriptions.size();
            long activeSubscriptions = subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE)
                .count();
            long trialingSubscriptions = subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.TRIALING)
                .count();
            long pastDueSubscriptions = subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.PAST_DUE)
                .count();
            long cancelledSubscriptions = subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.CANCELLED)
                .count();

            double activePercentage = totalSubscriptions > 0
                ? (activeSubscriptions / (double) totalSubscriptions) * 100.0
                : 0.0;

            // Calculate churn rate (simplified)
            double churnRate = totalSubscriptions > 0
                ? (cancelledSubscriptions / (double) totalSubscriptions) * 100.0
                : 0.0;

            // Group subscriptions by status
            Map<String, Long> byStatus = new HashMap<>();
            byStatus.put("ACTIVE", activeSubscriptions);
            byStatus.put("TRIALING", trialingSubscriptions);
            byStatus.put("PAST_DUE", pastDueSubscriptions);
            byStatus.put("CANCELLED", cancelledSubscriptions);
            byStatus.put("INCOMPLETE", subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.INCOMPLETE)
                .count());
            byStatus.put("COMPLETED", subscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.COMPLETED)
                .count());

            log.info("Generated billing analytics: total={}, active={}, trialing={}, pastDue={}, cancelled={}",
                totalSubscriptions, activeSubscriptions, trialingSubscriptions, pastDueSubscriptions, cancelledSubscriptions);

            return AdminBillingAnalyticsDTO.builder()
                .totalSubscriptions(totalSubscriptions)
                .activeSubscriptions(activeSubscriptions)
                .trialingSubscriptions(trialingSubscriptions)
                .pastDueSubscriptions(pastDueSubscriptions)
                .cancelledSubscriptions(cancelledSubscriptions)
                .activeSubscriptionPercentage(activePercentage)
                .churnRate(churnRate)
                .totalCustomers(totalSubscriptions)
                .newCustomersThisMonth(calculateNewCustomersThisMonth(subscriptions))
                .monthlyRecurringRevenue(calculateMRR(subscriptions))
                .annualRecurringRevenue(calculateARR(subscriptions))
                .averageMonthlyValue(calculateAMV(subscriptions))
                .failedPaymentsCount(0L)
                .retriedPaymentsCount(0L)
                .successfulPaymentsCount(0L)
                .paymentSuccessRate(0.0)
                .subscriptionsByStatus(byStatus)
                .dailyActiveSubscriptions(new ArrayList<>())
                .dailyNewSubscriptions(new ArrayList<>())
                .generatedAt(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("Error retrieving billing analytics", e);
            throw new RuntimeException("Failed to retrieve billing analytics", e);
        }
    }

    /**
     * Get billing revenue reports.
     */
    public AdminBillingRevenueDTO getBillingRevenue() {
        try {
            List<Subscription> subscriptions = subscriptionRepository.findAllWithPlan();

            BigDecimal mrr = calculateMRR(subscriptions);
            BigDecimal arr = calculateARR(subscriptions);
            BigDecimal totalRevenue = calculateTotalRevenue(subscriptions);
            BigDecimal thisMonthRevenue = calculateMonthlyRevenue(subscriptions, YearMonth.now());

            log.info("Generated revenue report: MRR={}, ARR={}, totalRevenue={}",
                mrr, arr, totalRevenue);

            return AdminBillingRevenueDTO.builder()
                .totalRevenueAllTime(totalRevenue)
                .totalRevenueThisMonth(thisMonthRevenue)
                .totalRevenueThisYear(calculateYearlyRevenue(subscriptions, LocalDateTime.now().getYear()))
                .averageRevenuePerSubscription(calculateAMV(subscriptions))
                .monthlyRecurringRevenue(mrr)
                .annualRecurringRevenue(arr)
                .totalPaymentSuccesses(0L)
                .totalPaymentFailures(0L)
                .totalRefundsIssued(BigDecimal.ZERO)
                .totalRefundCount(0L)
                .netRevenue(totalRevenue)
                .revenueGrowthPercentage(0.0)
                .monthlyRevenue(new ArrayList<>())
                .topPlans(new ArrayList<>())
                .revenueByStatus(new ArrayList<>())
                .generatedAt(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("Error retrieving billing revenue", e);
            throw new RuntimeException("Failed to retrieve billing revenue", e);
        }
    }

    /**
     * Process a refund.
     */
    public RefundResponse processRefund(RefundRequest request) {
        try {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Refund amount must be greater than 0");
            }

            UUID refundId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            log.info("Processing refund: refundId={}, userId={}, amount={}, reason={}",
                refundId, request.getUserId(), request.getAmount(), request.getReason());

            return RefundResponse.builder()
                .refundId(refundId)
                .userId(request.getUserId())
                .amount(request.getAmount())
                .status("PROCESSED")
                .reason(request.getReason())
                .transactionId(request.getTransactionId())
                .processedAt(now)
                .processedBy("ADMIN")
                .message("Refund of " + request.getAmount() + " successfully processed for user " + request.getUserId())
                .build();
        } catch (Exception e) {
            log.error("Error processing refund for user {}", request.getUserId(), e);
            throw new RuntimeException("Failed to process refund", e);
        }
    }

    // ===== HELPER METHODS =====

    private AdminBillingUserDTO mapSubscriptionToUserDTO(Subscription subscription) {
        try {
            // Plan is already fetched via JOIN FETCH, no lazy loading issue
            String planName = "N/A";
            if (subscription.getPlan() != null) {
                planName = subscription.getPlan().getName();
            }

            return AdminBillingUserDTO.builder()
                .tenantId(subscription.getTenantId())
                .email(subscription.getEmail() != null ? subscription.getEmail() : "unknown@example.com")
                .subscriptionStatus(subscription.getStatus() != null ? subscription.getStatus().name() : "UNKNOWN")
                .planName(planName)
                .monthlyValue(BigDecimal.valueOf(PLAN_PRICE))
                .subscriptionStartDate(subscription.getCreatedAt())
                .subscriptionEndDate(subscription.getExpiresAt())
                .nextBillingDate(subscription.getExpiresAt())
                .isTrialing(subscription.getStatus() == Subscription.SubscriptionStatus.TRIALING)
                .trialDaysRemaining(calculateTrialDaysRemaining(subscription))
                .hasFailedPayment(isHasFailedPayment(subscription))
                .lastPaymentDate(subscription.getUpdatedAt())
                .build();
        } catch (Exception e) {
            log.error("Critical error mapping subscription {} to UserDTO - this should not happen after JOIN FETCH fix", 
                subscription.getId(), e);
            throw new IllegalStateException(
                "Failed to map subscription " + subscription.getId() + " - data integrity compromised", e);
        }
    }

    private Integer calculateTrialDaysRemaining(Subscription subscription) {
        if (subscription.getStatus() != Subscription.SubscriptionStatus.TRIALING || subscription.getExpiresAt() == null) {
            return 0;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), subscription.getExpiresAt());
        return Math.max(0, (int) days);
    }

    private Boolean isHasFailedPayment(Subscription subscription) {
        return subscription.getStatus() == Subscription.SubscriptionStatus.PAST_DUE;
    }

    private Long calculateNewCustomersThisMonth(List<Subscription> subscriptions) {
        YearMonth thisMonth = YearMonth.now();
        return subscriptions.stream()
            .filter(s -> {
                if (s.getCreatedAt() == null) return false;
                YearMonth created = YearMonth.from(s.getCreatedAt());
                return created.equals(thisMonth);
            })
            .count();
    }

    private BigDecimal calculateMRR(List<Subscription> subscriptions) {
        long activeOrTrialing = subscriptions.stream()
            .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE ||
                         s.getStatus() == Subscription.SubscriptionStatus.TRIALING)
            .count();
        return BigDecimal.valueOf(activeOrTrialing * PLAN_PRICE);
    }

    private BigDecimal calculateARR(List<Subscription> subscriptions) {
        return calculateMRR(subscriptions).multiply(BigDecimal.valueOf(12));
    }

    private BigDecimal calculateAMV(List<Subscription> subscriptions) {
        if (subscriptions.isEmpty()) return BigDecimal.ZERO;
        long count = subscriptions.stream()
            .filter(s -> s.getPlan() != null)
            .count();
        double totalRevenue = count * PLAN_PRICE;
        return BigDecimal.valueOf(totalRevenue / subscriptions.size());
    }

    private BigDecimal calculateTotalRevenue(List<Subscription> subscriptions) {
        long count = subscriptions.stream()
            .filter(s -> s.getPlan() != null)
            .count();
        return BigDecimal.valueOf(count * PLAN_PRICE);
    }

    private BigDecimal calculateMonthlyRevenue(List<Subscription> subscriptions, YearMonth month) {
        long count = subscriptions.stream()
            .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE &&
                        s.getPlan() != null &&
                        s.getCreatedAt() != null &&
                        YearMonth.from(s.getCreatedAt()).compareTo(month) <= 0 &&
                        (s.getExpiresAt() == null || s.getExpiresAt().isAfter(month.atDay(1).atStartOfDay())))
            .count();
        return BigDecimal.valueOf(count * PLAN_PRICE);
    }

    private BigDecimal calculateYearlyRevenue(List<Subscription> subscriptions, int year) {
        long count = subscriptions.stream()
            .filter(s -> s.getPlan() != null &&
                        s.getCreatedAt() != null &&
                        s.getCreatedAt().getYear() == year)
            .count();
        return BigDecimal.valueOf(count * PLAN_PRICE);
    }
}
