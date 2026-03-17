package com.leadflow.backend.controller.settings;

import com.leadflow.backend.dto.settings.SettingResponse;
import com.leadflow.backend.entities.Setting;
import com.leadflow.backend.service.settings.SettingMapper;
import com.leadflow.backend.service.settings.SettingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

/**
 * Public endpoints for Settings (no authentication required).
 * These endpoints provide read-only access to public settings information.
 * 
 * Path: /public/settings
 */
@RestController
@RequestMapping("/public/settings")
public class PublicSettingController {

    private static final Logger log = LoggerFactory.getLogger(PublicSettingController.class);

    private final SettingService settingService;
    private final SettingMapper settingMapper;

    public PublicSettingController(
            SettingService settingService,
            SettingMapper settingMapper
    ) {
        this.settingService = settingService;
        this.settingMapper = settingMapper;
    }

    /* ======================================================
       GET BY ID (PUBLIC - Read Only)
       ====================================================== */

    @GetMapping("/{id}")
    public ResponseEntity<SettingResponse> getPublic(
            @PathVariable UUID id
    ) {
        log.debug("PUBLIC: Fetching public setting with id: {}", id);

        Setting setting = settingService.getById(id);

        return ResponseEntity.ok(settingMapper.toResponse(setting));
    }

    /* ======================================================
       EXCEPTION HANDLERS
       ====================================================== */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("PUBLIC: Validation error: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException e) {
        log.warn("PUBLIC: Illegal state: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Settings not found");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleEntityNotFound(EntityNotFoundException e) {
        log.warn("PUBLIC: Entity not found: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Resource not found");
    }
}
