package com.leadflow.backend.service.admin;

import com.leadflow.backend.dto.admin.*;
import com.leadflow.backend.entities.vendor.*;
import com.leadflow.backend.repository.*;
import com.leadflow.backend.service.notification.SendGridEmailService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
    private static final double PLAN_PRICE = 197.0;
    private static final int DEFAULT_GROWTH_DAYS = 30;
    private static final int DEFAULT_FORECAST_MONTHS = 6;
    private static final int MAX_FORECAST_MONTHS = 36;

    private final VendorRepository vendorRepository;
    private final VendorLeadRepository leadRepository;
    private final VendorUsageRepository usageRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final VendorRiskAlertRepository riskAlertRepository;
    private final SendGridEmailService emailService;

    @Value("${app.risk-alert.admin-email:}")
    private String riskAlertAdminEmail;

    public AdminService(
            VendorRepository vendorRepository,
            VendorLeadRepository leadRepository,
            VendorUsageRepository usageRepository,
            SubscriptionHistoryRepository historyRepository,
            VendorRiskAlertRepository riskAlertRepository,
            SendGridEmailService emailService
    ) {
        this.vendorRepository = Objects.requireNonNull(vendorRepository);
        this.leadRepository = Objects.requireNonNull(leadRepository);
        this.usageRepository = Objects.requireNonNull(usageRepository);
        this.historyRepository = Objects.requireNonNull(historyRepository);
        this.riskAlertRepository = Objects.requireNonNull(riskAlertRepository);
        this.emailService = Objects.requireNonNull(emailService);
    }

    /* ======================================================
       SAFETY HELPERS - CRÍTICO
       ====================================================== */

    /**
     * Divisão segura: evita NaN/Infinity
     */
    private double safeDivide(long numerator, long denominator, String context) {
        if (denominator == 0) {
            logger.warn("Division by zero detected in {}: numerator={}, returning 0", context, numerator);
            return 0;
        }
        double result = numerator / (double) denominator;
        if (!Double.isFinite(result)) {
            logger.warn("Non-finite result in {}: {} / {} = {}, returning 0", context, numerator, denominator, result);
            return 0;
        }
        return result;
    }

    /**
     * Garante que double é always finite
     */
    private double safeDouble(double value, String context) {
        if (!Double.isFinite(value)) {
            logger.warn("Non-finite double detected in {}: {}, returning 0", context, value);
            return 0;
        }
        return value;
    }

    /**
     * Seguro para BigDecimal
     */
    private BigDecimal safeBigDecimal(double value, String context) {
        double safe = safeDouble(value, context);
        return BigDecimal.valueOf(safe);
    }

    public AdminOverviewResponse getOverview() {
        long totalVendors = Optional.ofNullable(
                vendorRepository.countAllGlobal()
        ).orElse(0L);
        
        long active = Optional.ofNullable(
                vendorRepository.countBySubscriptionStatusGlobal(SubscriptionStatus.ATIVA.name())
        ).orElse(0L);
        
        long trial = Optional.ofNullable(
                vendorRepository.countBySubscriptionStatusGlobal(SubscriptionStatus.TRIAL.name())
        ).orElse(0L);
        
        long overdue = Optional.ofNullable(
                vendorRepository.countBySubscriptionStatusGlobal(SubscriptionStatus.INADIMPLENTE.name())
        ).orElse(0L);
        
        long expired = Optional.ofNullable(
                vendorRepository.countBySubscriptionStatusGlobal(SubscriptionStatus.EXPIRADA.name())
        ).orElse(0L);

        long totalLeads = Optional.ofNullable(
                leadRepository.countAllGlobal()
        ).orElse(0L);

        long totalAi = Optional.ofNullable(
                usageRepository.sumUsedByQuotaTypeGlobal(QuotaType.AI_EXECUTIONS.name())
        ).orElse(0L);

        double mrr = calculateMRR();
        double mrrReal = calculateMRR();
        double churnRate30d = calculateChurn(30);
        double trialConversion = calculateTrialConversion(30);
        double arpu = calculateARPU();
        double churnRate = calculateMonthlyChurn();
        double ltv = calculateLTV();

        return new AdminOverviewResponse(
                totalVendors,
                active,
                trial,
                overdue,
                expired,
                totalLeads,
                totalAi,
                safeBigDecimal(mrr, "overview.mrr"),
                safeBigDecimal(mrrReal, "overview.mrrReal"),
                safeDouble(churnRate30d, "overview.churnRate30d"),
                safeDouble(trialConversion, "overview.trialConversion"),
                safeBigDecimal(arpu, "overview.arpu"),
                safeDouble(churnRate, "overview.churnRate"),
                safeBigDecimal(ltv, "overview.ltv")
        );
    }

    /* ======================================================
       FINANCIAL METRICS
       ====================================================== */

    public double calculateMRR() {
        long active = Optional.ofNullable(
                vendorRepository.countActiveSubscriptionsGlobal()
        ).orElse(0L);
        return active * PLAN_PRICE;
    }

    public double calculateARPU() {

        long active = Optional.ofNullable(
                vendorRepository.countActiveSubscriptionsGlobal()
        ).orElse(0L);
        
        if (active == 0) {
            return 0;
        }

        return calculateMRR() / active;
    }

    public double calculateMonthlyChurn() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        Long cancellations = Optional.ofNullable(
                historyRepository.countCancellationsSinceGlobal(since)
        ).orElse(0L);
        
        Long activeBase = Optional.ofNullable(
                vendorRepository.countActiveSubscriptionsGlobal()
        ).orElse(0L);

        return safeDivide(cancellations, activeBase, "calculateMonthlyChurn");
    }

    public double calculateLTV() {
        double arpu = calculateARPU();
        double churn = calculateMonthlyChurn();

        if (churn == 0) {
            return arpu * 24;
        }

        if (churn >= 1.0) {
            logger.warn("Churn rate >= 1.0 detected, returning 0");
            return 0;
        }

        double ltv = arpu / churn;
        return safeDouble(ltv, "calculateLTV");
    }

    public double calculateChurn(int days) {
        int safeDays = days > 0 ? days : DEFAULT_GROWTH_DAYS;
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        Long cancellations = Optional.ofNullable(
                historyRepository.countCancellationsSinceGlobal(since)
        ).orElse(0L);
        
        Long activeBase = Optional.ofNullable(
                vendorRepository.countActiveSubscriptionsGlobal()
        ).orElse(0L);

        return safeDivide(cancellations, activeBase, "calculateChurn");
    }

    public double calculateTrialConversion(int days) {
        int safeDays = days > 0 ? days : DEFAULT_GROWTH_DAYS;
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        Long conversions = Optional.ofNullable(
                historyRepository.countTrialConversionsSinceGlobal(since)
        ).orElse(0L);
        
        Long trials = Optional.ofNullable(
                vendorRepository.countBySubscriptionStatusGlobal(SubscriptionStatus.TRIAL.name())
        ).orElse(0L);

        return safeDivide(conversions, trials, "calculateTrialConversion");
    }

    /* ======================================================
       GROWTH
       ====================================================== */

    public GrowthResponse getGrowth(int days) {

        int safeDays = days > 0 ? days : DEFAULT_GROWTH_DAYS;
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        List<GrowthPoint> vendors = mapToPoints(
                vendorRepository.countVendorsPerDayGlobal(since)
        );

        List<GrowthPoint> leads = mapToPoints(
                leadRepository.countLeadsPerDayGlobal(since)
        );

        List<GrowthPoint> ai = mapToPoints(
                usageRepository.sumUsagePerDayGlobal(
                        QuotaType.AI_EXECUTIONS.name(),
                        since
                )
        );

        List<GrowthPoint> revenue = vendors.stream()
                .map(p -> new GrowthPoint(
                        p.getDate(),
                        Math.round(p.getValue() * PLAN_PRICE)
                ))
                .toList();

        return new GrowthResponse(vendors, revenue, leads, ai);
    }

    /* ======================================================
       COHORT
       ====================================================== */

    public List<CohortResponse> calculateCohorts() {

        List<Vendor> vendors = vendorRepository.findAllWithSubscriptionStart();

        Map<YearMonth, List<Vendor>> cohorts =
                vendors.stream()
                        .collect(Collectors.groupingBy(v ->
                                YearMonth.from(
                                        v.getSubscriptionStartedAt()
                                                .atZone(ZoneOffset.UTC)
                                )
                        ));

        List<CohortResponse> result = new ArrayList<>();

        cohorts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {

                    YearMonth cohortMonth = entry.getKey();
                    List<Vendor> cohortVendors = entry.getValue();
                    int total = cohortVendors.size();

                    Map<Integer, Double> retention = new LinkedHashMap<>();

                    for (int month = 0; month <= 12; month++) {

                        YearMonth checkMonth = cohortMonth.plusMonths(month);

                        long active =
                                cohortVendors.stream()
                                        .filter(v -> isActiveInMonth(v, checkMonth))
                                        .count();

                        double percentage =
                                total == 0 ? 0 : (active / (double) total) * 100;

                        retention.put(month, percentage);
                    }

                    result.add(new CohortResponse(cohortMonth.toString(), retention));
                });

        return result;
    }

    /* ======================================================
       FORECAST
       ====================================================== */

    public List<ForecastPoint> forecastMRR(int months) {
        int safeMonths =
                months <= 0
                        ? DEFAULT_FORECAST_MONTHS
                        : Math.min(months, MAX_FORECAST_MONTHS);

        // CRÍTICO: sanitizar antes de usar
        double churnRate = safeDouble(calculateMonthlyChurn(), "forecastMRR.churnRate");
        double conversionRate = safeDouble(calculateTrialConversion(30), "forecastMRR.conversionRate");

        long active = Optional.ofNullable(
                vendorRepository.countBySubscriptionStatusGlobal(
                        SubscriptionStatus.ATIVA.name()
                )
        ).orElse(0L);

        long trials = Optional.ofNullable(
                vendorRepository.countBySubscriptionStatusGlobal(
                        SubscriptionStatus.TRIAL.name()
                )
        ).orElse(0L);

        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        List<ForecastPoint> forecast = new ArrayList<>();

        for (int month = 1; month <= safeMonths; month++) {
            long churned = active > 0 ? Math.round(Math.max(0, Math.min(active, (long)(active * churnRate)))) : 0;
            long converted = trials > 0 ? Math.round(Math.max(0, Math.min(trials, (long)(trials * conversionRate)))) : 0;

            active = Math.max(0, active + converted - churned);

            double projectedMRR = active * PLAN_PRICE;
            
            forecast.add(
                    new ForecastPoint(
                            current.plusMonths(month).toString(),
                            safeDouble(projectedMRR, "forecastMRR.projectedMRR")
                    )
            );
        }

        return forecast;
    }

    /* ======================================================
       HEALTH SCORE
       ====================================================== */

    public VendorHealthResponse calculateHealth(@NonNull UUID vendorId) {
        UUID safeVendorId = Objects.requireNonNull(vendorId);

        Optional<Vendor> vendorOpt = vendorRepository.findById(safeVendorId);
        
        if (vendorOpt.isEmpty()) {
            logger.error("Vendor not found for health calculation: {}", safeVendorId);
            throw new IllegalArgumentException("Vendor não encontrado: " + safeVendorId);
        }

        Vendor vendor = vendorOpt.get();

        double score =
                scoreUsage(vendor) * 0.30 +
                scoreLeads(vendor) * 0.25 +
                scoreRecency(vendor) * 0.20 +
                scoreSubscription(vendor) * 0.15 +
                scorePaymentHistory(vendor) * 0.10;

        int finalScore = Math.min(100, (int) Math.round(safeDouble(score, "calculateHealth.score")));

        String riskLevel =
                finalScore >= 75 ? "LOW"
                        : finalScore >= 50 ? "MEDIUM"
                        : "HIGH";

        logger.debug("Health calculation for {}: score={}, riskLevel={}", safeVendorId, finalScore, riskLevel);
        return new VendorHealthResponse(safeVendorId, finalScore, riskLevel);
    }

    public void evaluateRisk(@NonNull UUID vendorId) {

        UUID safeVendorId = Objects.requireNonNull(vendorId);

        VendorHealthResponse health = calculateHealth(safeVendorId);

        boolean highRisk =
                "HIGH".equals(health.getRiskLevel()) ||
                health.getScore() < 50;

        if (!highRisk) {
            return;
        }

        boolean exists =
                riskAlertRepository.existsByVendorIdAndResolvedFalse(safeVendorId);

        if (exists) {
            return;
        }

        VendorRiskAlert alert = new VendorRiskAlert();
        alert.setVendorId(safeVendorId);
        alert.setScore(health.getScore());
        alert.setRiskLevel(health.getRiskLevel());

        riskAlertRepository.save(alert);

        notifyVendorAtRisk(safeVendorId, health);
    }

    public void evaluateAllVendorsRisk() {

        vendorRepository.findAll()
                                .stream()
                                .map(Vendor::getId)
                                .filter(Objects::nonNull)
                                .forEach(this::evaluateRisk);
    }

    public void evaluateAllVendorsRiskDaily() {
        vendorRepository.findAll()
            .stream()
            .map(Vendor::getId)
            .filter(Objects::nonNull)
            .forEach(this::evaluateRisk);
    }

    private void notifyVendorAtRisk(UUID vendorId,
                                    VendorHealthResponse health) {

        UUID safeVendorId = Objects.requireNonNull(vendorId);

        Vendor vendor = vendorRepository.findById(safeVendorId)
                .orElseThrow(() -> new RuntimeException("Vendor não encontrado"));

        String html = """
                <h2>Estamos percebendo pouca atividade</h2>
                <p>Seu uso do Leadflow AI caiu recentemente.</p>
                <p>Health Score atual: <strong>%d</strong></p>
                <p>Se precisar de ajuda, nossa equipe está pronta para apoiar.</p>
                """.formatted(health.getScore());

        emailService.sendEmail(
                vendor.getUserEmail(),
                "Percebemos pouca atividade na sua conta",
                html
        );
    }

    /* ======================================================
       SCORE COMPONENTS
       ====================================================== */

    private int scoreUsage(Vendor vendor) {

        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        long usage = Optional.ofNullable(
                usageRepository.sumLast30Days(
                        vendor.getId(),
                        QuotaType.AI_EXECUTIONS.name(),
                        since
                )
        ).orElse(0L);

        if (usage > 500) return 100;
        if (usage > 200) return 80;
        if (usage > 50) return 50;

        return 20;
    }

    private int scoreLeads(Vendor vendor) {

        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        long leads = leadRepository.countLast30Days(vendor.getId(), since);

        if (leads > 50) return 100;
        if (leads > 20) return 70;
        if (leads > 5) return 40;

        return 15;
    }

    private int scoreRecency(Vendor vendor) {

        Instant last = usageRepository.lastActivity(vendor.getId());

        if (last == null) return 10;

        long days = Duration.between(last, Instant.now()).toDays();

        if (days <= 3) return 100;
        if (days <= 7) return 80;
        if (days <= 14) return 50;

        return 20;
    }

    private int scoreSubscription(Vendor vendor) {

        SubscriptionStatus status = vendor.getSubscriptionStatus();

        if (status == null) {
            return 0;
        }

        return switch (status) {
            case ATIVA -> 100;
            case TRIAL -> 70;
            case INADIMPLENTE -> 20;
            case EXPIRADA -> 10;
            default -> 0;
        };
    }

    private int scorePaymentHistory(Vendor vendor) {

        return vendor.getSubscriptionStatus() ==
                SubscriptionStatus.INADIMPLENTE ? 10 : 80;
    }

    /* ======================================================
       HELPERS
       ====================================================== */

    private boolean isActiveInMonth(Vendor vendor, YearMonth month) {

        if (vendor.getSubscriptionStatus() != SubscriptionStatus.ATIVA) {
            return false;
        }

        Instant start = vendor.getSubscriptionStartedAt();
        Instant end = vendor.getSubscriptionExpiresAt();

        if (start == null) return false;

        YearMonth vendorStart =
                YearMonth.from(start.atZone(ZoneOffset.UTC));

        if (vendorStart.isAfter(month)) return false;

        if (end != null) {

            YearMonth vendorEnd =
                    YearMonth.from(end.atZone(ZoneOffset.UTC));

            return !vendorEnd.isBefore(month);
        }

        return true;
    }

    private List<GrowthPoint> mapToPoints(List<Object[]> rows) {

        return rows.stream()
                .map(r -> new GrowthPoint(
                        toLocalDate(r[0]),
                        ((Number) r[1]).longValue()
                ))
                .toList();
    }

    private LocalDate toLocalDate(Object rawDate) {

        if (rawDate instanceof LocalDate d) return d;

        if (rawDate instanceof Date d) return d.toLocalDate();

        return LocalDate.parse(rawDate.toString());
    }
}