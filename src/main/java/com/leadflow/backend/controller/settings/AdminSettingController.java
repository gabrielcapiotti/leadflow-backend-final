package com.leadflow.backend.controller.settings;

import com.leadflow.backend.dto.settings.SettingResponse;
import com.leadflow.backend.dto.settings.UpdateSettingRequest;
import com.leadflow.backend.entities.Setting;
import com.leadflow.backend.service.settings.SettingMapper;
import com.leadflow.backend.service.settings.SettingService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

/**
 * Admin-only endpoints for Settings management by ID.
 * These endpoints allow admins/internal services to manage any setting by its ID.
 * 
 * Path: /api/settings/{id}
 */
@RestController
@RequestMapping("/api/settings")
public class AdminSettingController {

    private static final Logger log = LoggerFactory.getLogger(AdminSettingController.class);

    private final SettingService settingService;
    private final SettingMapper settingMapper;

    public AdminSettingController(
            SettingService settingService,
            SettingMapper settingMapper
    ) {
        this.settingService = settingService;
        this.settingMapper = settingMapper;
    }

    /* ======================================================
       GET BY ID (ADMIN)
       ====================================================== */

    @GetMapping("/{id}")
    public ResponseEntity<SettingResponse> getById(
            @PathVariable UUID id
    ) {
        log.debug("Admin: Fetching setting with id: {}", id);

        Setting setting = settingService.getById(id);

        return ResponseEntity.ok(settingMapper.toResponse(setting));
    }

    /* ======================================================
       PUT BY ID (ADMIN)
       ====================================================== */

    @PutMapping("/{id}")
    public ResponseEntity<SettingResponse> updateById(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSettingRequest request
    ) {
        log.info("Admin: Updating setting with id: {}", id);

        Setting setting = settingService.updateById(
                id,
                request.getVendorName(),
                request.getWhatsapp(),
                request.getCompanyName(),
                request.getLogo(),
                request.getWelcomeMessage()
        );

        log.info("Admin: Setting updated successfully for id: {}", id);
        return ResponseEntity.ok(settingMapper.toResponse(setting));
    }

    /* ======================================================
       DELETE BY ID (ADMIN)
       ====================================================== */

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(
            @PathVariable UUID id
    ) {
        log.info("Admin: Deleting setting with id: {}", id);

        settingService.softDeleteById(id);

        log.info("Admin: Setting deleted successfully for id: {}", id);
        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       EXCEPTION HANDLERS
       ====================================================== */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        log.error("Admin: Validation error: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException e) {
        log.warn("Admin: Illegal state: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Settings not found");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleEntityNotFound(EntityNotFoundException e) {
        log.error("Admin: Entity not found: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Resource not found");
    }
}
