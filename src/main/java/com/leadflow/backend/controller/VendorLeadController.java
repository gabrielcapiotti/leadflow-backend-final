package com.leadflow.backend.controller;

import com.leadflow.backend.dto.vendor.CreateLeadRequest;
import com.leadflow.backend.dto.vendor.StageConversionResponse;
import com.leadflow.backend.dto.vendor.StageTimeMetricsResponse;
import com.leadflow.backend.dto.vendor.UpdateStageRequest;
import com.leadflow.backend.dto.vendor.VendorLeadMetricsResponse;
import com.leadflow.backend.entities.vendor.VendorLead;
import com.leadflow.backend.entities.vendor.VendorLeadAlert;
import com.leadflow.backend.entities.vendor.VendorLeadConversation;
import com.leadflow.backend.repository.VendorLeadAlertRepository;
import com.leadflow.backend.security.SubscriptionGuard;
import com.leadflow.backend.service.vendor.ResumoService;
import com.leadflow.backend.service.vendor.VendorLeadService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = {"/vendor-leads", "/api/vendor-leads"})
public class VendorLeadController {

    private final VendorLeadService service;
    private final ResumoService resumoService;
    private final VendorLeadAlertRepository alertRepository;
    private final SubscriptionGuard subscriptionGuard;

    public VendorLeadController(VendorLeadService service,
                                ResumoService resumoService,
                                VendorLeadAlertRepository alertRepository,
                                SubscriptionGuard subscriptionGuard) {
        this.service = service;
        this.resumoService = resumoService;
        this.alertRepository = alertRepository;
        this.subscriptionGuard = subscriptionGuard;
    }

    /* ======================================================
       CREATE
       ====================================================== */

    @PostMapping("/leads")
    @Transactional
    public ResponseEntity<VendorLead> createLead(
            @Valid @RequestBody CreateLeadRequest request) {

        subscriptionGuard.assertFullAccess();

        VendorLead createdLead = service.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdLead);
    }

    /* ======================================================
       READ
       ====================================================== */

    @GetMapping("/{id}")
    public ResponseEntity<VendorLead> getById(@PathVariable UUID id) {

        subscriptionGuard.assertActive();

        return ResponseEntity.ok(service.getLeadForCurrentVendor(id));
    }

    @GetMapping
    public ResponseEntity<Page<VendorLead>> list(Pageable pageable) {

        subscriptionGuard.assertActive();

        return ResponseEntity.ok(service.listForCurrentVendor(pageable));
    }

    /* ======================================================
       DELETE
       ====================================================== */

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLead(@PathVariable UUID id) {

        subscriptionGuard.assertFullAccess();

        service.deleteLead(id);

        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       UPDATE
       ====================================================== */

    @PutMapping("/{id}/stage")
    public ResponseEntity<VendorLead> updateStage(
            @PathVariable UUID id,
            @RequestBody UpdateStageRequest request) {

        subscriptionGuard.assertFullAccess();

        VendorLead updated =
                service.updateStage(id, request.getStage());

        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VendorLead> updateLeadPatch(
            @PathVariable UUID id,
            @RequestBody UpdateStageRequest request) {

        subscriptionGuard.assertFullAccess();

        VendorLead updated =
                service.updateStage(id, request.getStage());

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/owner")
    public ResponseEntity<VendorLead> assignOwner(
            @PathVariable UUID id) {

        subscriptionGuard.assertFullAccess();

        return ResponseEntity.ok(service.assignOwner(id));
    }

    /* ======================================================
       METRICS
       ====================================================== */

    @GetMapping("/metrics")
    public ResponseEntity<VendorLeadMetricsResponse> getMetrics() {

        subscriptionGuard.assertActive();

        return ResponseEntity.ok(service.getMetricsForCurrentVendor());
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

    /* ======================================================
       EXTRA
       ====================================================== */

    @GetMapping("/ranking")
    public ResponseEntity<List<VendorLead>> getRanking() {

        subscriptionGuard.assertActive();

        return ResponseEntity.ok(service.getRankingForCurrentVendor());
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

        subscriptionGuard.assertFullAccess();

        return ResponseEntity.ok(resumoService.gerarResumo(id));
    }
}