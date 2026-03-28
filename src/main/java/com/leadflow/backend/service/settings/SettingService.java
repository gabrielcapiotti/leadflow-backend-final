package com.leadflow.backend.service.settings;

import com.leadflow.backend.entities.Setting;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.exception.ResourceNotFoundException;
import com.leadflow.backend.repository.settings.SettingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SettingService {

    private final SettingRepository settingRepository;

    public SettingService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    /* ======================================================
       CREATE OR UPDATE
       ====================================================== */

    @Transactional
    public Setting saveOrUpdate(
            User user,
            String vendorName,
            String whatsapp,
            String companyName,
            String logo,
            String welcomeMessage
    ) {

        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        Setting setting = settingRepository
                .findByUser(user)
                .orElse(null);

        if (setting == null) {

            // 🆕 Criação
            setting = new Setting(
                    user,
                    vendorName,
                    whatsapp,
                    companyName,
                    logo,
                    welcomeMessage
            );

        } else {

            // 🔄 Reativação segura (sem recriar entidade)
            if (setting.isDeleted()) {
                setting.restore();
            }

            // 🔄 Atualização parcial segura
            setting.update(
                    vendorName != null ? vendorName : setting.getVendorName(),
                    whatsapp != null ? whatsapp : setting.getWhatsapp(),
                    companyName != null ? companyName : setting.getCompanyName(),
                    logo != null ? logo : setting.getLogo(),
                    welcomeMessage != null ? welcomeMessage : setting.getWelcomeMessage()
            );
        }

        return settingRepository.save(setting);
    }

    /* ======================================================
       READ
       ====================================================== */

    @Transactional(readOnly = true)
    public Setting getByUser(User user) {

        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        return settingRepository.findByUser(user)
                .filter(setting -> !setting.isDeleted())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Settings not found for user id=" + user.getId()
                        )
                );
    }

    @Transactional(readOnly = true)
    public Setting getById(UUID id) {

        if (id == null) {
            throw new IllegalArgumentException("Setting id cannot be null");
        }

        return settingRepository.findById(id)
                .filter(setting -> !setting.isDeleted())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Setting not found"));
    }

    /* ======================================================
       SOFT DELETE
       ====================================================== */

    @Transactional
    public void softDelete(User user) {

        Setting setting = getByUser(user);

        if (!setting.isDeleted()) {
            setting.softDelete();
            settingRepository.save(setting);
        }
    }

    @Transactional
    public void softDeleteById(UUID id) {

        Setting setting = getById(id);

        if (!setting.isDeleted()) {
            setting.softDelete();
            settingRepository.save(setting);
        }
    }

    /* ======================================================
       UPDATE BY ID
       ====================================================== */

    @Transactional
    public Setting updateById(
            UUID id,
            String vendorName,
            String whatsapp,
            String companyName,
            String logo,
            String welcomeMessage
    ) {

        Setting setting = getById(id);

        if (setting.isDeleted()) {
            setting.restore();
        }

        setting.update(
                vendorName != null ? vendorName : setting.getVendorName(),
                whatsapp != null ? whatsapp : setting.getWhatsapp(),
                companyName != null ? companyName : setting.getCompanyName(),
                logo != null ? logo : setting.getLogo(),
                welcomeMessage != null ? welcomeMessage : setting.getWelcomeMessage()
        );

        return settingRepository.save(setting);
    }

    /* ======================================================
       PARTIAL UPDATE (PATCH)
       ====================================================== */

    @Transactional
    public Setting partialUpdate(
            User user,
            String vendorName,
            String whatsapp,
            String companyName,
            String logo,
            String welcomeMessage
    ) {

        Setting setting = getByUser(user);

        if (setting.isDeleted()) {
            throw new IllegalStateException("Cannot update deleted settings");
        }

        // Atualiza apenas os campos não-nulos
        if (vendorName != null && !vendorName.isBlank()) {
            setting.setVendorName(vendorName);
        }
        if (whatsapp != null && !whatsapp.isBlank()) {
            setting.setWhatsapp(whatsapp);
        }
        if (companyName != null && !companyName.isBlank()) {
            setting.setCompanyName(companyName);
        }
        if (logo != null && !logo.isBlank()) {
            setting.setLogo(logo);
        }
        if (welcomeMessage != null && !welcomeMessage.isBlank()) {
            setting.setWelcomeMessage(welcomeMessage);
        }

        return settingRepository.save(setting);
    }

    /* ======================================================
       PUBLIC SETTINGS
       ====================================================== */

    @Transactional(readOnly = true)
    public Setting getPublicSettings(UUID settingId) {

        if (settingId == null) {
            throw new IllegalArgumentException("Setting id cannot be null");
        }

        // Retorna settings públicos sem verificar autenticação
        // (validação de acesso é feita no controller)
        return settingRepository.findById(settingId)
                .filter(setting -> !setting.isDeleted())
                .orElseThrow(() ->
                        new IllegalArgumentException("Setting not found")
                );
    }

    /* ======================================================
       RESET TO DEFAULTS
       ====================================================== */

    @Transactional
    public Setting resetToDefaults(User user) {

        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        // Try to find active (non-deleted) setting first
        Setting setting = settingRepository
                .findByUser(user)
                .orElse(null);

        // If no active setting found, check for deleted one to restore
        if (setting == null) {
            setting = settingRepository
                    .findByUserIncludingDeleted(user)
                    .orElse(null);
            
            // If still nothing, create new
            if (setting == null) {
                setting = new Setting(
                        user,
                        "My Vendor",
                        "+55 11 9999-9999", 
                        "My Company",
                        null,
                        "Welcome to my profile!"
                );
                return settingRepository.save(setting);
            }
            
            // Restore the deleted setting
            setting.restore();
        }

        // Reset all fields to defaults (idempotent)
        setting.setVendorName("My Vendor");
        setting.setWhatsapp("+55 11 9999-9999");
        setting.setCompanyName("My Company");
        setting.setLogo(null);
        setting.setWelcomeMessage("Welcome to my profile!");
        
        // Save and return (always 200 OK - idempotent)
        return settingRepository.save(setting);
    }
}