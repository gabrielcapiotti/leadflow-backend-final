package com.leadflow.backend.controller.lead;

import com.leadflow.backend.dto.lead.CreateLeadRequest;
import com.leadflow.backend.dto.lead.LeadResponse;
import com.leadflow.backend.entities.enums.LeadStatus;
import com.leadflow.backend.entities.lead.Lead;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.entities.vendor.SubscriptionAccessLevel;
import com.leadflow.backend.security.CustomUserDetails;
import com.leadflow.backend.security.SubscriptionGuard;
import com.leadflow.backend.service.lead.LeadService;
import com.leadflow.backend.service.user.UserService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = {"/leads", "/api/leads"})
public class LeadController {

    private static final Logger log = LoggerFactory.getLogger(LeadController.class);

    private final LeadService leadService;
    private final UserService userService;
    private final SubscriptionGuard subscriptionGuard;

    public LeadController(
            LeadService leadService,
            UserService userService,
            SubscriptionGuard subscriptionGuard
    ) {
        this.leadService = leadService;
        this.userService = userService;
        this.subscriptionGuard = subscriptionGuard;
    }

    /* ======================================================
       CREATE
       ====================================================== */

    @PostMapping
    public ResponseEntity<LeadResponse> createLead(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateLeadRequest request
    ) {

        try {
            enforceWriteAccess();
            User user = resolveAuthenticatedUser(principal);
            log.info("Creating lead for user: {}", user.getId());
            // NOTE: Email NOT logged - sensitive data
            Lead lead = leadService.createLead(
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                user
            );
            log.info("Lead created successfully with ID: {}", lead.getId());
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new LeadResponse(lead));
        } catch (IllegalArgumentException e) {
            log.warn("Lead creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("Unexpected error during lead creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /* ======================================================
       LIST
       ====================================================== */

    @GetMapping
    public ResponseEntity<List<LeadResponse>> listLeads(
            @AuthenticationPrincipal UserDetails principal
    ) {

        User user = resolveAuthenticatedUser(principal);
        log.debug("Listing active leads for user: {}", user.getId());

        List<LeadResponse> response = leadService
                .listActiveLeads(user)
                .stream()
                .map(LeadResponse::new)
                .toList();

        log.debug("Found {} active leads for user: {}", response.size(), user.getId());
        return ResponseEntity.ok(response);
    }

    /* ======================================================
       GET BY ID
       ====================================================== */

    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> getLeadDetails(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id
    ) {

        User user = resolveAuthenticatedUser(principal);
        log.debug("Fetching lead {} details for user: {}", id, user.getId());

        try {
            Lead lead = leadService.getByIdForUser(id, user.getId());
            log.debug("Lead {} retrieved successfully", id);
            return ResponseEntity.ok(new LeadResponse(lead));
        } catch (EntityNotFoundException e) {
            log.warn("Lead {} not found for user {}", id, user.getId());
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Lead not found"
            );
        }
    }

    /* ======================================================
       UPDATE STATUS
       ====================================================== */

    @PatchMapping("/{id}/status")
    public ResponseEntity<LeadResponse> updateLeadStatus(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @RequestParam LeadStatus status
    ) {

        enforceWriteAccess();

        User user = resolveAuthenticatedUser(principal);
        log.info("Updating lead {} status to: {}", id, status);

        Lead lead = leadService.updateStatus(id, status, user);

        log.info("Lead {} status updated successfully", id);
        return ResponseEntity.ok(new LeadResponse(lead));
    }

    /* ======================================================
       DELETE (SOFT DELETE)
       ====================================================== */

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLead(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id
    ) {

        enforceWriteAccess();

        User user = resolveAuthenticatedUser(principal);
        log.info("Deleting lead: {}", id);

        leadService.softDelete(id, user);

        log.info("Lead {} deleted successfully", id);
        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       INTERNAL
       ====================================================== */

    private void enforceWriteAccess() {

        if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
            log.warn("Write access denied due to insufficient subscription level");
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Subscription does not allow write operations"
            );
        }
    }

    private User resolveAuthenticatedUser(UserDetails principal) {

        if (principal == null) {
            log.warn("Authentication principal is null");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // If principal is CustomUserDetails, it was already validated by JWT filter
        if (principal instanceof CustomUserDetails customUser) {
            log.debug("User resolved from principal: {}", customUser.getUsername());
            return customUser.getUser();
        }

        // Fallback: Query database (may fail if user in different tenant schema)
        try {
            User user = userService.getActiveByEmail(principal.getUsername());
            log.debug("User resolved successfully: {}", principal.getUsername());
            return user;
        } catch (IllegalArgumentException e) {
            log.warn("User not found by email '{}': {}", principal.getUsername(), e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user not found"
            );
        }
    }

    /* ======================================================
       EXCEPTION HANDLERS
       ====================================================== */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        log.error("Validation error: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(e.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleEntityNotFound(EntityNotFoundException e) {
        log.error("Entity not found: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Resource not found");
    }
}