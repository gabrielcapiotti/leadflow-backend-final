package com.leadflow.backend.service.vendor;

import com.leadflow.backend.entities.vendor.VendorFeature;
import com.leadflow.backend.entities.vendor.VendorFeatureKey;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.VendorFeatureRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VendorFeatureService {

    private final VendorFeatureRepository vendorFeatureRepository;

    public VendorFeatureService(VendorFeatureRepository vendorFeatureRepository) {
        this.vendorFeatureRepository = vendorFeatureRepository;
    }

    /**
     * Verifica se um recurso está habilitado para um vendor.
     * Respeita o isolamento de multi-tenancy usando TenantContext.
     * 
     * ✅ Usado por controllers e endpoints (onde TenantContext está sempre definido)
     *
     * @param vendorId UUID do vendor
     * @param featureKey chave da feature
     * @return true se a feature está habilitada, false caso contrário
     */
    public boolean isEnabled(UUID vendorId, VendorFeatureKey featureKey) {
        UUID tenantId = TenantContext.getTenant();
        return vendorFeatureRepository
                .findByTenantIdAndVendorIdAndFeatureKey(tenantId, vendorId, featureKey)
                .map(VendorFeature::isEnabled)
                .orElse(false);
    }

    /**
     * 🔥 UPSERT COM TENANT EXPLÍCITO (NOVO - RECOMENDADO)
     * 
     * Insere ou atualiza um recurso de vendor.
     * ✅ Recebe tenantId como parâmetro explícito (não usa TenantContext)
     * 
     * QUANDO USAR:
     * - Onboarding (createDefaultSubscription)
     * - Operações admin que precisam de tenant específico
     * - Qualquer lugar que NÃO tenha TenantContext disponível
     *
     * @param tenantId UUID do tenant (EXPLÍCITO)
     * @param vendorId UUID do vendor
     * @param featureKey chave da feature
     * @param enabled se a feature deve ser habilitada
     */
    public void upsertFeature(UUID tenantId, UUID vendorId, VendorFeatureKey featureKey, boolean enabled) {
        VendorFeature feature = vendorFeatureRepository
                .findByTenantIdAndVendorIdAndFeatureKey(tenantId, vendorId, featureKey)
                .orElseGet(() -> {
                    VendorFeature created = new VendorFeature();
                    created.setTenantId(tenantId);
                    created.setVendorId(vendorId);
                    created.setFeatureKey(featureKey);
                    return created;
                });

        feature.setEnabled(enabled);
        vendorFeatureRepository.save(feature);
    }

    /**
     * ⚠️ UPSERT COM TENANTCONTEXT (LEGADO)
     * 
     * Insere ou atualiza um recurso de vendor usando TenantContext.
     * ⚠️ DEPRECATED em favor de upsertFeature(tenantId, vendorId, featureKey, enabled)
     * 
     * QUANDO USAR:
     * - Controllers/endpoints onde TenantContext está garantido
     * - Backward compatibility com código antigo
     *
     * @param vendorId UUID do vendor
     * @param featureKey chave da feature
     * @param enabled se a feature deve ser habilitada
     * @deprecated Use upsertFeature(UUID tenantId, ...) instead
     */
    @Deprecated(forRemoval = true)
    public void upsertFeature(UUID vendorId, VendorFeatureKey featureKey, boolean enabled) {
        UUID tenantId = TenantContext.getTenant();
        upsertFeature(tenantId, vendorId, featureKey, enabled);
    }
}
