package com.leadflow.backend.controller;

import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.service.subscription.TrialService;
import com.leadflow.backend.service.vendor.UsageService;
import com.leadflow.backend.service.vendor.QuotaService;
import com.leadflow.backend.service.PlanService;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

@RestController
@RequestMapping(value = {"/vendors", "/api/vendors"})
public class VendorController {

    private final VendorRepository repository;
    private final TrialService trialService;
    private final UsageService usageService;
    private final QuotaService quotaService;
    private final PlanService planService;
    private static final Logger logger = Logger.getLogger(VendorController.class.getName());

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
    
    // 🔒 DTO para CREATE (segurança - cliente não controla entidade JPA)
    public static record CreateVendorRequest(
        String slug,
        String name,
        String nomeVendedor,
        String whatsappVendedor,
        String nomeEmpresa,
        String logoUrl,
        String corDestaque,
        String mensagemBoasVindas
    ) {}

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
       CREATE (IDEMPOTENTE + SEGURA)
       ====================================================== */

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody @NonNull CreateVendorRequest req) {
        String tenant = TenantContext.getTenant();
        
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context required");
        }

        // 🔥 CRÍTICO: Vendor.id = tenantId significa 1:1
        // Se vendor já existe para este tenant, retornar 409 CONFLICT
        UUID tenantUUID;
        try {
            tenantUUID = UUID.fromString(tenant);
        } catch (IllegalArgumentException e) {
            // Public tenant é named, não UUID
            // Usar um hash do tenant name como ID determinístico
            tenantUUID = UUID.nameUUIDFromBytes(tenant.getBytes());
        }
        
        // 🔥 IDEMPOTÊNCIA: Se vendor já existe para este tenant, retornar existente
        var existingVendor = repository.findByIdAndTenantId(tenantUUID, tenant);
        if (existingVendor.isPresent()) {
            logger.info("⚠️ Vendor already exists for tenant: " + tenant + ", returning 409 CONFLICT");
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, 
                "Vendor already exists for this tenant"
            );
        }

        // 🔒 valida slug único por tenant
        if (repository.existsBySlugAndTenantId(req.slug(), tenant)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Slug already exists in tenant"
            );
        }

        // 🔒 Construir vendor apenas com campos seguros
        Vendor vendor = new Vendor();
        vendor.setId(tenantUUID); // 🔥 CRÍTICO: Alinhamento de identidade
        vendor.setTenantId(tenant);
        vendor.setSlug(req.slug());
        vendor.setName(req.name());
        vendor.setNomeVendedor(req.nomeVendedor());
        vendor.setWhatsappVendedor(req.whatsappVendedor() != null ? req.whatsappVendedor() : "0000000000");
        vendor.setNomeEmpresa(req.nomeEmpresa());
        vendor.setLogoUrl(req.logoUrl());
        vendor.setCorDestaque(req.corDestaque());
        vendor.setMensagemBoasVindas(req.mensagemBoasVindas());
        vendor.setEmailInvalid(false);
        vendor.setCreatedAt(Instant.now());
        
        // 🔥 Associar usuário autenticado
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String userEmail = auth.getName();
            if (userEmail != null && !userEmail.isBlank()) {
                vendor.setUserEmail(userEmail);
            }
        }

        try {
            trialService.initializeTrial(vendor);
            Vendor saved = repository.save(vendor);
            usageService.initializeUsage(saved.getId(), planService.getActivePlan());
            quotaService.initializePlanLimits(saved.getId());
            trialService.enableTrialFeatures(saved);
            
            logger.info("✅ Vendor created successfully: " + saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            logger.severe("❌ Error creating vendor: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create vendor", e);
        }
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