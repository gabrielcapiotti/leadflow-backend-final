package com.leadflow.backend.repository;

import com.leadflow.backend.entities.vendor.VendorFeature;
import com.leadflow.backend.entities.vendor.VendorFeatureKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VendorFeatureRepository extends JpaRepository<VendorFeature, UUID> {

    Optional<VendorFeature> findByVendorIdAndFeatureKey(UUID vendorId,
                                                        VendorFeatureKey featureKey);

    /**
     * Encontra um recurso de vendor isolado por tenant.
     * Este método respeita a política de isolamento de multi-tenancy.
     *
     * @param tenantId tenant UUID (obrigatório)
     * @param vendorId vendor UUID
     * @param featureKey feature key
     * @return Optional contendo o VendorFeature se encontrado, vazio caso contrário
     */
    Optional<VendorFeature> findByTenantIdAndVendorIdAndFeatureKey(UUID tenantId,
                                                                   UUID vendorId,
                                                                   VendorFeatureKey featureKey);
}
