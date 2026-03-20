package com.leadflow.backend.controller;

import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.service.subscription.TrialService;
import com.leadflow.backend.service.vendor.UsageService;
import com.leadflow.backend.service.vendor.QuotaService;
import com.leadflow.backend.service.PlanService;

import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping(value = {"/vendors", "/api/vendors"})
public class VendorController {

    private static final Logger log = LoggerFactory.getLogger(VendorController.class);

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
        this.repository =
                Objects.requireNonNull(repository, "VendorRepository must not be null");

        this.trialService =
                Objects.requireNonNull(trialService, "TrialService must not be null");
        
        this.usageService =
                Objects.requireNonNull(usageService, "UsageService must not be null");

        this.quotaService =
                Objects.requireNonNull(quotaService, "QuotaService must not be null");
        
        this.planService =
                Objects.requireNonNull(planService, "PlanService must not be null");
    }

    /* ======================================================
       FILTER
       ====================================================== */

    @GetMapping
    public List<Vendor> filter(
            @RequestParam(required = false) String user_email,
            @RequestParam(required = false) String slug
    ) {

        if (user_email != null) {
            return repository.findByUserEmail(user_email);
        }

        if (slug != null) {
            return repository.findBySlug(slug)
                    .map(List::of)
                    .orElse(List.of());
        }

        return List.of();
    }

    /* ======================================================
       CREATE
       ====================================================== */

    @PostMapping
    @Transactional
    public Vendor create(
            @RequestBody @NonNull Vendor vendor
    ) {

        Vendor safeVendor =
                Objects.requireNonNull(vendor, "Vendor must not be null");

        // Ensure all required fields are set with defaults
        safeVendor.setEmailInvalid(false);
        
        // Set user email - HARDCODED FOR NOW TO TEST
        if (safeVendor.getUserEmail() == null || safeVendor.getUserEmail().isBlank()) {
            safeVendor.setUserEmail("carlos@leadflow.com");  // HARDCODED TO TEST
        }
        
        // Ensure createdAt is set (fixing @PrePersist not being called)
        if (safeVendor.getCreatedAt() == null) {
            safeVendor.setCreatedAt(Instant.now());
        }

        try {
            log.info("🔧 [VENDOR CREATE] Starting vendor creation. Email: {}, Slug: {}", 
                safeVendor.getUserEmail(), safeVendor.getSlug());
            
            trialService.initializeTrial(safeVendor);
            log.info("✅ [VENDOR CREATE] Trial initialized. Subscription expires at: {}", 
                safeVendor.getSubscriptionExpiresAt());
            
            Vendor savedVendor = repository.save(safeVendor);
            log.info("✅ [VENDOR CREATE] Vendor saved! ID: {}, createdAt: {}", 
                savedVendor.getId(), savedVendor.getCreatedAt());
            
            // Initialize usage limits for the newly created vendor
            try {
                usageService.initializeUsage(savedVendor.getId(), planService.getActivePlan());
                log.info("✅ [VENDOR CREATE] Usage service initialized");
            } catch (Exception e) {
                log.error("❌ [VENDOR CREATE] Error in usageService.initializeUsage: {}", e.getMessage(), e);
            }
            
            // Initialize quota tracking for the newly created vendor
            try {
                quotaService.initializePlanLimits(savedVendor.getId());
                log.info("✅ [VENDOR CREATE] Quota service initialized");
            } catch (Exception e) {
                log.error("❌ [VENDOR CREATE] Error in quotaService.initializePlanLimits: {}", e.getMessage(), e);
            }
            
            // Enable trial features (including AI_CHAT)
            try {
                trialService.enableTrialFeatures(savedVendor);
                log.info("✅ [VENDOR CREATE] Trial features enabled");
            } catch (Exception e) {
                log.error("❌ [VENDOR CREATE] Error in trialService.enableTrialFeatures: {}", e.getMessage(), e);
            }

            log.info("✅✅ [VENDOR CREATE] Vendor creation COMPLETE! ID: {}", savedVendor.getId());
            return savedVendor;
        } catch (Exception e) {
            log.error("❌ [VENDOR CREATE] CRITICAL ERROR in vendor creation: {}", e.getMessage(), e);
            throw e;
        }
    }

    /* ======================================================
       UPDATE
       ====================================================== */

    @PutMapping("/{id}")
    public Vendor update(
            @PathVariable @NonNull UUID id,
            @RequestBody @NonNull Vendor data
    ) {

        UUID safeId =
                Objects.requireNonNull(id, "Vendor id must not be null");

        Vendor safeData =
                Objects.requireNonNull(data, "Vendor data must not be null");

        Vendor vendor = repository.findById(safeId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Vendor not found: " + safeId)
                );

        vendor.setNomeVendedor(safeData.getNomeVendedor());
        vendor.setWhatsappVendedor(safeData.getWhatsappVendedor());
        vendor.setNomeEmpresa(safeData.getNomeEmpresa());
        vendor.setLogoUrl(safeData.getLogoUrl());
        vendor.setCorDestaque(safeData.getCorDestaque());
        vendor.setMensagemBoasVindas(safeData.getMensagemBoasVindas());
        vendor.setSlug(safeData.getSlug());

        if (safeData.getSubscriptionStatus() != null) {
            vendor.setSubscriptionStatus(safeData.getSubscriptionStatus());
        }

        return repository.save(vendor);
    }

    /* ======================================================
       DELETE
       ====================================================== */

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable @NonNull UUID id
    ) {
        UUID safeId =
                Objects.requireNonNull(id, "Vendor id must not be null");

        Vendor vendor = repository.findById(safeId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Vendor not found: " + safeId)
                );

        repository.delete(vendor);
    }
}