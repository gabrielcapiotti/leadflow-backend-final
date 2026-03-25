package com.leadflow.backend.controller.lead;

import com.leadflow.backend.dto.lead.LeadStatusHistoryResponse;
import com.leadflow.backend.entities.lead.Lead;
import com.leadflow.backend.entities.lead.LeadStatusHistory;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.security.CustomUserDetails;
import com.leadflow.backend.service.lead.LeadService;
import com.leadflow.backend.service.lead.LeadStatusHistoryService;
import com.leadflow.backend.service.user.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/history")
public class LeadStatusHistoryController {

    private static final Logger log = LoggerFactory.getLogger(LeadStatusHistoryController.class);

    private final LeadService leadService;
    private final LeadStatusHistoryService historyService;
    private final UserService userService;

    public LeadStatusHistoryController(
            LeadService leadService,
            LeadStatusHistoryService historyService,
            UserService userService
    ) {
        this.leadService = leadService;
        this.historyService = historyService;
        this.userService = userService;
    }

    /* ======================================================
       HISTORY BY LEAD (ISOLATED BY USER)
       ====================================================== */

    @GetMapping("/leads/{leadId}")
    public ResponseEntity<List<LeadStatusHistoryResponse>> getHistory(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID leadId
    ) {

        User user = resolveUser(principal);
        log.debug("Fetching history for lead: {} by user: {}", leadId, user.getId());

        Lead lead = leadService.getByIdForUser(leadId, user.getId());

        if (lead == null) {
            log.warn("Lead not found: {}", leadId);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Lead not found"
            );
        }

        List<LeadStatusHistoryResponse> response =
                historyService.getHistoryByLead(lead);

        log.debug("Found {} history records for lead: {}", response.size(), leadId);
        return ResponseEntity.ok(response);
    }

    /* ======================================================
       HISTORY BY HISTORY ID (ISOLATED BY USER)
       ====================================================== */

    @Transactional(readOnly = true)
    @GetMapping("/{historyId}")
    public ResponseEntity<LeadStatusHistoryResponse> getHistoryById(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID historyId
    ) {

        User user = resolveUser(principal);

        try {
            LeadStatusHistory history = historyService.getById(historyId);

            if (history.getLead() == null ||
                !history.getLead().getUserId().equals(user.getId())) {

                // Evita vazamento de existência entre usuários
                return ResponseEntity.notFound().build();
            }

            LeadStatusHistoryResponse response =
                    new LeadStatusHistoryResponse(
                            history.getId(),
                            history.getStatus(),
                            history.getChangedAt(),
                            history.getChangedBy() != null
                                    ? history.getChangedBy().getEmail()
                                    : "SYSTEM"
                    );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // History not found
            log.warn("History not found: {}", historyId);
            return ResponseEntity.notFound().build();
        }
    }
    

    /* ======================================================
       INTERNAL
       ====================================================== */

    private User resolveUser(UserDetails principal) {

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
        User user = userService.getActiveByEmail(principal.getUsername());

        if (user == null) {
            log.warn("Authenticated user not found: {}", principal.getUsername());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user not found"
            );
        }

        return user;
    }

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