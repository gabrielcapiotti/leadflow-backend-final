package com.leadflow.backend.controller;

import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.service.subscription.TrialService;
import com.leadflow.backend.service.vendor.UsageService;
import com.leadflow.backend.service.vendor.QuotaService;
import com.leadflow.backend.service.PlanService;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping(value = {"/vendors", "/api/vendors"})
public class VendorController {

    private final VendorRepository repository;
    private final TrialService trialService;
    private final UsageService usageService;
    private final QuotaService quotaService;
    private final PlanService planService;

    public VendorController(
            VendorRepository repository,
            TrialService trialService,
            UsageService usageService,
            QuotaService quotaService,
            PlanService planService
    ) {
        this.repository = repository;
        this.trialService = trialService;
        this.usageService = usageService;
        this.quotaService = quotaService;
        this.planService = planService;
    }

    /* ======================================================
       FILTER (SEGURA)
       ====================================================== */

    @GetMapping
    public List<Vendor> filter(
            @RequestParam(required = false) String user_email,
            @RequestParam(required = false) String slug
    ) {
        String tenant = TenantContext.getTenant();

        if (user_email != null) {
            return repository.findByUserEmailAndTenantId(user_email, tenant);
        }

        if (slug != null) {
            return repository.findBySlugAndTenantId(slug, tenant)
                    .map(List::of)
                    .orElse(List.of());
        }

        return repository.findAllByTenantId(tenant);
    }

    /* ======================================================
       GET BY ID (SEGURA)
       ====================================================== */

    @GetMapping("/{id}")
    public ResponseEntity<Vendor> getById(@PathVariable UUID id) {
        String tenant = TenantContext.getTenant();
        return repository.findByIdAndTenantId(id, tenant)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /* ======================================================
       CREATE (SEGURA)
       ====================================================== */

    @PostMapping
    @Transactional
    public Vendor create(@RequestBody @NonNull Vendor vendor) {

        String tenant = TenantContext.getTenant();

        Vendor safe = Objects.requireNonNull(vendor);

        // 🔥 CRÍTICO: set tenant
        safe.setTenantId(tenant);

        // 🔥 CRÍTICO: associar usuário autenticado ao vendor
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String userEmail = auth.getName();
            if (userEmail != null && !userEmail.isBlank()) {
                safe.setUserEmail(userEmail);
            }
        }

        // defaults
        safe.setEmailInvalid(false);

        if (safe.getCreatedAt() == null) {
            safe.setCreatedAt(Instant.now());
        }

        // 🔒 valida slug único por tenant
        if (repository.existsBySlugAndTenantId(safe.getSlug(), tenant)) {
            throw new IllegalArgumentException("Slug already exists in tenant");
        }

        trialService.initializeTrial(safe);

        Vendor saved = repository.save(safe);

        usageService.initializeUsage(saved.getId(), planService.getActivePlan());
        quotaService.initializePlanLimits(saved.getId());
        trialService.enableTrialFeatures(saved);

        return saved;
    }

    /* ======================================================
       UPDATE (SEGURA)
       ====================================================== */

    @PutMapping("/{id}")
    public Vendor update(
            @PathVariable @NonNull UUID id,
            @RequestBody @NonNull Vendor data
    ) {
        String tenant = TenantContext.getTenant();

        Vendor vendor = repository
                .findByIdAndTenantId(id, tenant)
                .orElseThrow(() ->
                        new IllegalArgumentException("Vendor not found")
                );

        vendor.setNomeVendedor(data.getNomeVendedor());
        vendor.setWhatsappVendedor(data.getWhatsappVendedor());
        vendor.setNomeEmpresa(data.getNomeEmpresa());
        vendor.setLogoUrl(data.getLogoUrl());
        vendor.setCorDestaque(data.getCorDestaque());
        vendor.setMensagemBoasVindas(data.getMensagemBoasVindas());
        vendor.setSlug(data.getSlug());
        vendor.setName(data.getName());

        return repository.save(vendor);
    }

    /* ======================================================
       DELETE (SEGURA)
       ====================================================== */

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {

        String tenant = TenantContext.getTenant();

        Vendor vendor = repository
                .findByIdAndTenantId(id, tenant)
                .orElseThrow(() ->
                        new IllegalArgumentException("Vendor not found")
                );

        repository.delete(vendor);
    }
}