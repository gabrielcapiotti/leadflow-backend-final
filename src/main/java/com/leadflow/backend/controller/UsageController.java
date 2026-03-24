package com.leadflow.backend.controller;

import com.leadflow.backend.entities.UsageLimit;
import com.leadflow.backend.security.SubscriptionGuard;
import com.leadflow.backend.security.VendorContext;
import com.leadflow.backend.service.vendor.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/billing/usage")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UsageController {

    private final UsageService usageService;
    private final VendorContext vendorContext;
    private final SubscriptionGuard subscriptionGuard;

    // ================= GET USAGE =================

    @GetMapping
    public ResponseEntity<UsageLimit> getUsage() {
        return resolveUsage();
    }

    // ================= LIMITS =================

    @GetMapping("/limits")
    public ResponseEntity<UsageLimit> getUsageLimits() {
        return resolveUsage();
    }

    // ================= CORE LOGIC =================

    private ResponseEntity<UsageLimit> resolveUsage() {
        try {
            if (!subscriptionGuard.isActive()) {
                log.warn("Inactive subscription");
                return ResponseEntity.status(403).build();
            }

            UUID vendorId = vendorContext.getCurrentVendorId();

            if (vendorId == null) {
                log.warn("VendorContext returned null vendorId");
                return ResponseEntity.noContent().build();
            }

            UsageLimit usage = usageService.getUsage(vendorId);

            if (usage == null) {
                log.warn("UsageService returned null for vendor {}", vendorId);
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(usage);
        } catch (Exception e) {
            log.warn("Exception in resolveUsage: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.noContent().build();
        }
    }
}