package com.leadflow.backend.service.vendor;

import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.vendor.SubscriptionStatus;
import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.repository.PlanRepository;
import com.leadflow.backend.repository.VendorRepository;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final UsageService usageService;
    private final PlanRepository planRepository;

    public VendorService(
            VendorRepository vendorRepository,
            UsageService usageService,
            PlanRepository planRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.usageService = usageService;
        this.planRepository = planRepository;
    }

    @Transactional
    public Vendor createVendor(String email) {
        Vendor vendor = new Vendor();
        vendor.setUserEmail(normalizeEmail(email));
        vendor.setName(localPart(email));
        vendor.setNomeVendedor(localPart(email));
        vendor.setWhatsappVendedor("0000000000");
        vendor.setSlug(generateSlug(email));
        vendor.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        Vendor savedVendor = vendorRepository.save(vendor);

        // Inicializar usage para o novo vendor com o plano ativo padrão
        try {
            Plan defaultPlan = planRepository.findByActiveTrue()
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active plan found"));
            usageService.initializeUsage(savedVendor.getId(), defaultPlan);
        } catch (Exception e) {
            // Log mas não quebra - vendor foi criado mas usage não foi inicializado
            // Isto será inicializado on-demand se necessário
            System.err.println("⚠️ Erro ao inicializar usage para vendor: " + e.getMessage());
        }

        return savedVendor;
    }

    @Transactional
    public Vendor ensureVendorExists(String email) {
        String normalizedEmail = normalizeEmail(email);
        return vendorRepository.findFirstByUserEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> createVendor(normalizedEmail));
    }

    private String generateSlug(String email) {
        String prefix = localPart(email)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (prefix.isBlank()) {
            prefix = "vendor";
        }

        return prefix + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String localPart(String email) {
        if (email == null || !email.contains("@")) {
            return "vendor";
        }
        return email.substring(0, email.indexOf('@'));
    }

    @Transactional
    public void assignSchema(Vendor vendor, String schema) {
        vendor.setSchemaName(schema);
        vendorRepository.save(vendor);
    }
}
