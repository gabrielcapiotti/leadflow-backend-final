package com.leadflow.backend.controller;

import com.leadflow.backend.dto.vendor.CreateLeadRequest;
import com.leadflow.backend.dto.vendor.StageConversionResponse;
import com.leadflow.backend.dto.vendor.StageTimeMetricsResponse;
import com.leadflow.backend.dto.vendor.UpdateStageRequest;
import com.leadflow.backend.dto.vendor.VendorLeadMetricsResponse;
import com.leadflow.backend.entities.vendor.SubscriptionAccessLevel;
import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.entities.vendor.VendorLeadAlert;
import com.leadflow.backend.entities.vendor.VendorLeadConversation;
import com.leadflow.backend.entities.vendor.VendorLead;
import com.leadflow.backend.repository.VendorLeadAlertRepository;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.security.SubscriptionGuard;
import com.leadflow.backend.service.vendor.ResumoService;
import com.leadflow.backend.service.vendor.VendorLeadService;
import com.leadflow.backend.service.vendor.VendorService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = {"/vendor-leads", "/api/vendor-leads"})
public class VendorLeadController {

    private final VendorLeadService service;
    private final ResumoService resumoService;
    private final VendorLeadAlertRepository alertRepository;
    private final SubscriptionGuard subscriptionGuard;
    private final VendorService vendorService;
    private final VendorRepository vendorRepository;

    public VendorLeadController(VendorLeadService service,
                                ResumoService resumoService,
                                VendorLeadAlertRepository alertRepository,
                                SubscriptionGuard subscriptionGuard,
                                VendorService vendorService,
                                VendorRepository vendorRepository) {
        this.service = service;
        this.resumoService = resumoService;
        this.alertRepository = alertRepository;
        this.subscriptionGuard = subscriptionGuard;
        this.vendorService = vendorService;
        this.vendorRepository = vendorRepository;
    }

    private void ensureVendorExists() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new IllegalStateException("User not authenticated");
            }

            String userEmail = auth.getName();
            boolean hasVendorRole = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"));

            if (!hasVendorRole) {
                throw new IllegalStateException("User does not have VENDOR role");
            }

            // Check if vendor already exists for this email
            boolean vendorExists = vendorRepository.findFirstByUserEmailIgnoreCase(userEmail).isPresent();
            if (!vendorExists) {
                System.out.println("🔧 AUTO-CREATING VENDOR for: " + userEmail);
                // Auto-create vendor for user with VENDOR role
                Vendor created = vendorService.createVendor(userEmail);
                System.out.println("✅ VENDOR CREATED: " + created.getId());
            } else {
                System.out.println("✅ VENDOR EXISTS for: " + userEmail);
            }
        } catch (Exception e) {
            System.err.println("❌ Vendor creation check failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    @PostMapping("/leads")
    @Transactional
    public ResponseEntity<?> createLead(
            @Valid @RequestBody CreateLeadRequest request) {

        // Ensure vendor exists FIRST (before any guard that depends on VendorContext)
        ensureVendorExists();

        if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
            return ResponseEntity.status(403).body(
                Map.of(
                    "error", "SUBSCRIPTION_READ_ONLY",
                    "message", "Assinatura não permite criar leads."
                )
            );
        }

        // Vendor already exists at this point, service.create() will find it
        VendorLead createdLead = service.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdLead);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendorLead> getById(@PathVariable UUID id) {

        subscriptionGuard.assertActive();
        ensureVendorExists();

        try {
            return ResponseEntity.ok(service.getLeadForCurrentVendor(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLead(@PathVariable UUID id) {

        if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ensureVendorExists();
        service.deleteLead(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<VendorLead>> list(Pageable pageable) {

        subscriptionGuard.assertActive();
        ensureVendorExists();

        return ResponseEntity.ok(service.listForCurrentVendor(pageable));
    }

    @PutMapping("/{id}/stage")
    public ResponseEntity<?> updateStage(
            @PathVariable UUID id,
            @RequestBody UpdateStageRequest request) {

        if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
            return ResponseEntity.status(403).body(
                Map.of(
                    "error", "SUBSCRIPTION_READ_ONLY",
                    "message", "Assinatura não permite editar leads."
                )
            );
        }

        ensureVendorExists();

        try {
            VendorLead updated =
                    service.updateStage(id, request.getStage());

            return ResponseEntity.ok(updated);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<VendorLeadMetricsResponse> getMetrics() {

        subscriptionGuard.assertActive();

        return ResponseEntity.ok(service.getMetricsForCurrentVendor());
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<VendorLead>> getRanking() {

        subscriptionGuard.assertActive();

        return ResponseEntity.ok(
            service.getRankingForCurrentVendor()
        );
    }

    @PutMapping("/{id}/owner")
    public ResponseEntity<VendorLead> assignOwner(
            @PathVariable UUID id) {

        if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(
                service.assignOwner(id)
        );
    }

    @GetMapping("/metrics/stage-time")
        public ResponseEntity<StageTimeMetricsResponse> getStageTimeMetrics() {

        subscriptionGuard.assertActive();

        return ResponseEntity.ok(
            service.calculateAverageStageTimeForCurrentVendor()
        );
    }

    @GetMapping("/metrics/conversion")
        public ResponseEntity<StageConversionResponse> getConversionMetrics() {

        subscriptionGuard.assertActive();

        return ResponseEntity.ok(
            service.calculateConversionRatesForCurrentVendor()
        );
    }

    @GetMapping("/{id}/conversation")
    public ResponseEntity<List<VendorLeadConversation>> getConversation(
            @PathVariable UUID id) {

        subscriptionGuard.assertActive();

        service.getLeadForCurrentVendor(id);

        return ResponseEntity.ok(service.getConversation(id));
    }

    @GetMapping("/{id}/alerts")
    public ResponseEntity<List<VendorLeadAlert>> getOpenAlerts(
            @PathVariable UUID id) {

        subscriptionGuard.assertActive();

        service.getLeadForCurrentVendor(id);

        return ResponseEntity.ok(
                alertRepository.findByVendorLeadIdAndResolvidoFalseOrderByCreatedAtDesc(id)
        );
    }

    @PutMapping("/{id}/resumo")
    public ResponseEntity<String> gerarResumo(@PathVariable UUID id) {

        if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
            return ResponseEntity.status(403).body("Assinatura não permite editar leads.");
        }

        String resumo = resumoService.gerarResumo(id);

        return ResponseEntity.ok(resumo);
    }
}