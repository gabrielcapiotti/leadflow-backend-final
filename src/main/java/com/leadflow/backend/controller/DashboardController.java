package com.leadflow.backend.controller;

import com.leadflow.backend.dto.vendor.DashboardResponse;
import com.leadflow.backend.service.vendor.DashboardService;
import com.leadflow.backend.security.SubscriptionGuard;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SubscriptionGuard subscriptionGuard;

    public DashboardController(
            DashboardService dashboardService,
            SubscriptionGuard subscriptionGuard
    ) {
        this.dashboardService = dashboardService;
        this.subscriptionGuard = subscriptionGuard;
    }

    /**
     * Get dashboard data for current vendor
     *
     * Returns:
     * - 200 OK with data
     * - 204 No Content if no data available
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardResponse> getDashboard() {

        // 🔒 Enforce subscription/business rules
        subscriptionGuard.assertActive();

        DashboardResponse response = dashboardService.getDashboardForCurrentVendor();

        // ⚠️ No data scenario (determinístico)
        if (response == null) {
            return ResponseEntity.noContent().build(); // 204
        }

        // ✅ Success
        return ResponseEntity.ok(response); // 200
    }
}