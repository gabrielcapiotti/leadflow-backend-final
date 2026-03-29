package com.leadflow.backend.service.vendor;

import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.entities.vendor.SubscriptionStatus;
import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.PlanRepository;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.repository.user.UserRepository;

import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class VendorService {

    @PersistenceContext
    private EntityManager entityManager;

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final UsageService usageService;
    private final PlanRepository planRepository;

    public VendorService(
            VendorRepository vendorRepository,
            UserRepository userRepository,
            UsageService usageService,
            PlanRepository planRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
        this.usageService = usageService;
        this.planRepository = planRepository;
    }

    @Transactional
    public Vendor createVendor(User user) {
        // 🔥 CRÍTICO: Chave de lookup = (email + tenantId) para isolamento multi-tenant
        // Antes fazia lookup por email APENAS, causando vendor leakage entre tenants
        var existingVendor = vendorRepository.findFirstByUserEmailIgnoreCaseAndTenantId(
                normalizeEmail(user.getEmail()),
                user.getTenantId()
        );
        
        if (existingVendor.isPresent()) {
            return existingVendor.get();
        }

        // ✅ Criar novo vendor apenas se não existir
        return createVendorInternal(user);
    }

    /**
     * INTERNO APENAS: Cria novo vendor sem verificação de duplicata
     * Deve ser chamado APENAS durante fluxo de registro
     * 
     * 🔴 CRÍTICO: Não chama usageService.initializeUsage()
     *    Side-effects devem ser síncronos e orquestrados no controller/service de nível superior
     */
    @Transactional
    private Vendor createVendorInternal(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and userId cannot be null");
        }

        // 🔥 CRÍTICO: Validar tenantId ANTES de converter para UUID
        if (user.getTenantId() == null || user.getTenantId().isBlank()) {
            throw new IllegalStateException("User tenantId is null or blank - cannot create vendor");
        }

        UUID tenantUUID = UUID.fromString(user.getTenantId());
        
        // 🔥 CRÍTICO: Verificar se vendor já existe (concorrência)
        if (vendorRepository.existsById(tenantUUID)) {
            return vendorRepository.findById(tenantUUID).get();
        }

        Vendor vendor = new Vendor();
        
        // 🔥 CRÍTICO: vendor.id DEVE ser igual ao tenantId para alinhamento de identidade
        // Isso garante que subscription.tenant_id (FK) possa referenciar vendor.id corretamente
        vendor.setId(tenantUUID);
        
        vendor.setUserEmail(normalizeEmail(user.getEmail()));
        // ✅ FIXO: Name agora é único com suffix (evita constraint violation)
        vendor.setName(generateUniqueName(user));
        vendor.setNomeVendedor(user.getName());
        vendor.setWhatsappVendedor("0000000000");
        vendor.setSlug(generateSlug(user.getName()));
        vendor.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        
        // ✅ CRÍTICO: Usar tenantId do User, não do TenantContext
        vendor.setTenantId(user.getTenantId());
        
        // 🔥 FIX: Usar persist() em vez de save() para evitar erro de optimistic locking
        // Quando ID é manual (preenchido), save() usa merge() que presume existência
        // persist() é correto para novas entidades mesmo com ID manual
        try {
            entityManager.persist(vendor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist vendor: " + e.getMessage(), e);
        }
        
        // 🔴 REMOVIDO: usageService.initializeUsage() 
        // Side-effects críticos devem ser síncronos
        // Orquestrados no nível de negócio (RegisterService, etc)

        return vendor;
    }

    /**
     * ⚠️ OVERLOAD APENAS PARA CASOS ESPECIAIS (ex: BillingValidationInterceptor)
     * 
     * Deve ser usado com CUIDADO:
     * - Usa TenantContext apenas quando User não está disponível
     * - Idempotente (retorna vendor existente se houver)
     */
    @Transactional
    public Vendor createVendor(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }

        String normalizedEmail = normalizeEmail(email);
        String tenantId = TenantContext.getTenant();
        
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is not available");
        }
        
        // 🔥 CRÍTICO: Chave de lookup = (email + tenantId) para isolamento multi-tenant
        var existingVendor = vendorRepository.findFirstByUserEmailIgnoreCaseAndTenantId(normalizedEmail, tenantId);
        if (existingVendor.isPresent()) {
            return existingVendor.get();
        }

        // ✅ Criar novo vendor apenas se não existir
        Vendor vendor = new Vendor();
        
        vendor.setUserEmail(normalizedEmail);
        // ✅ FIXO: Name agora é único com suffix
        vendor.setName(email + "-" + UUID.randomUUID().toString().substring(0, 6));
        vendor.setNomeVendedor(localPart(email));
        vendor.setWhatsappVendedor("0000000000");
        vendor.setSlug(generateSlug(normalizedEmail));
        vendor.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        
        // ⚠️ Fallback: usa TenantContext apenas quando User não está disponível
        vendor.setTenantId(TenantContext.getTenant());
        
        Vendor savedVendor = vendorRepository.save(vendor);

        // 🔴 REMOVIDO: usageService.initializeUsage()
        // Side-effects críticos devem ser síncronos

        return savedVendor;
    }

    @Transactional
    public Vendor ensureVendorExists(UUID userId) {
        throw new IllegalStateException(
                "❌ CRITICAL: ensureVendorExists() is FORBIDDEN\n" +
                "Vendor MUST be created during registration ONLY\n" +
                "Not on login, not on interceptor, not on refresh\n" +
                "Use createVendor() during @RegisterFlow or through RegisterService"
        );
    }

    /**
     * ✅ FIXO: Gera nome único para vendor adicionando UUID suffix
     * Evita constraint violation de uq_vendors_name
     */
    private String generateUniqueName(User user) {
        String baseName = user.getName() != null ? user.getName() : "vendor";
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return baseName + "-" + suffix;
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
