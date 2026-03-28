package com.leadflow.backend.controller.settings;

import com.leadflow.backend.dto.settings.PatchSettingRequest;
import com.leadflow.backend.dto.settings.SettingResponse;
import com.leadflow.backend.dto.settings.UpdateSettingRequest;
import com.leadflow.backend.entities.Setting;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.security.CustomUserDetails;
import com.leadflow.backend.service.settings.SettingMapper;
import com.leadflow.backend.service.settings.SettingService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/me/settings")
public class SettingController {

    private static final Logger log = LoggerFactory.getLogger(SettingController.class);

    private final SettingService settingService;
    private final SettingMapper settingMapper;

    public SettingController(
            SettingService settingService,
            SettingMapper settingMapper
    ) {
        this.settingService = settingService;
        this.settingMapper = settingMapper;
    }

    /* ======================================================
       GET (isolado por usuário)
       ====================================================== */

    @GetMapping
    public ResponseEntity<SettingResponse> get(
            @AuthenticationPrincipal UserDetails principal
    ) {

        User user = resolveUser(principal);
        log.debug("GET /api/me/settings - user: {}", user.getId());

        Setting setting = settingService.getByUser(user);

        return ResponseEntity.ok(settingMapper.toResponse(setting));
    }

    /* ======================================================
       GET BY ID (ADMIN / uso interno)
       ====================================================== */

    @GetMapping("/{id}")
    public ResponseEntity<SettingResponse> getById(
            @PathVariable UUID id
    ) {

        Setting setting = settingService.getById(id);

        return ResponseEntity.ok(settingMapper.toResponse(setting));
    }

    /* ======================================================
       SAVE OR UPDATE
       ====================================================== */

    @PutMapping
    public ResponseEntity<SettingResponse> saveOrUpdate(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateSettingRequest request
    ) {

        User user = resolveUser(principal);
        log.info("PUT /api/me/settings - user: {}", user.getId());

        Setting setting = settingService.saveOrUpdate(
                user,
                request.getVendorName(),
                request.getWhatsapp(),
                request.getCompanyName(),
                request.getLogo(),
                request.getWelcomeMessage()
        );

        log.info("PUT /api/me/settings successful - user: {}", user.getId());
        return ResponseEntity.ok(settingMapper.toResponse(setting));
    }

    /* ======================================================
       DELETE (soft delete por usuário)
       ====================================================== */

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails principal
    ) {

        User user = resolveUser(principal);
        log.info("DELETE /api/me/settings - user: {}", user.getId());

        settingService.softDelete(user);

        log.info("DELETE /api/me/settings successful - user: {}", user.getId());
        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       PATCH (Partial Update)
       ====================================================== */

    @PatchMapping
    public ResponseEntity<SettingResponse> partialUpdate(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody PatchSettingRequest request
    ) {

        User user = resolveUser(principal);
        log.info("PATCH /api/me/settings - user: {}", user.getId());

        Setting setting = settingService.partialUpdate(
                user,
                request.getVendorName(),
                request.getWhatsapp(),
                request.getCompanyName(),
                request.getLogo(),
                request.getWelcomeMessage()
        );

        log.info("PATCH /api/me/settings successful - user: {}", user.getId());
        return ResponseEntity.ok(settingMapper.toResponse(setting));
    }

    /* ======================================================
       RESET TO DEFAULTS
       ====================================================== */

    @PostMapping("/reset")
    public ResponseEntity<SettingResponse> resetToDefaults(
            @AuthenticationPrincipal UserDetails principal
    ) {

        User user = resolveUser(principal);
        log.info("POST /api/me/settings/reset - user: {}", user.getId());

        Setting setting = settingService.resetToDefaults(user);

        log.info("POST /api/me/settings/reset successful - user: {}", user.getId());
        return ResponseEntity.ok(settingMapper.toResponse(setting));
    }

    /* ======================================================
       INTERNAL
       ====================================================== */

    private User resolveUser(UserDetails principal) {

        if (principal == null) {
            log.warn("AUTH: Principal is null");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User not authenticated"
            );
        }

        if (!(principal instanceof CustomUserDetails customUser)) {
            log.error("AUTH: Principal is not CustomUserDetails: {}", principal.getClass().getName());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication"
            );
        }

        log.debug("AUTH: User resolved - {}", customUser.getUsername());
        return customUser.getUser();
    }
}
