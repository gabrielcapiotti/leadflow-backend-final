package com.leadflow.backend.security;

import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.security.exception.UnauthorizedException;
import com.leadflow.backend.multitenancy.context.TenantContext;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class VendorContext {

    private final VendorRepository vendorRepository;

    public VendorContext(VendorRepository vendorRepository) {
        this.vendorRepository = Objects.requireNonNull(
                vendorRepository,
                "vendorRepository cannot be null"
        );
    }

    /**
     * Retorna o Vendor associado ao usuário autenticado.
     *
     * Fluxo:
     * JWT Filter → SecurityContextHolder → VendorContext → VendorRepository
     * 
     * ⚠️ MULTI-TENANT AWARE:
     * Busca por (email, tenantId) para garantir isolamento
     */
    public Vendor getCurrentVendor() {

        Authentication authentication = getAuthentication();

        String email = authentication.getName();

        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Authenticated user email not found");
        }

        // ✅ Normalizar email: lowercase + trim (deve ser igual ao que foi salvo em Vendor)
        String normalizedEmail = email.trim().toLowerCase();

        String tenant = TenantContext.getTenant();
        
        if (tenant == null || tenant.isBlank()) {
            throw new UnauthorizedException("Tenant context not resolved - cannot lookup vendor");
        }

        return vendorRepository
                .findFirstByUserEmailIgnoreCaseAndTenantId(normalizedEmail, tenant)
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Authenticated user does not belong to any vendor in tenant '" + tenant + "'"
                        ));
    }

    /**
     * Retorna apenas o ID do Vendor atual.
     * Evita carregar a entidade completa quando não necessário.
     */
    public UUID getCurrentVendorId() {
        return getCurrentVendor().getId();
    }

    /**
     * Obtém a autenticação atual com validações de segurança.
     */
    private Authentication getAuthentication() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new UnauthorizedException("No authentication found in security context");
        }

        if (!authentication.isAuthenticated()) {
            throw new UnauthorizedException("User is not authenticated");
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Anonymous authentication is not allowed");
        }

        return authentication;
    }
}