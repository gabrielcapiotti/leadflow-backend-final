package com.leadflow.backend.multitenancy.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Component
public class TenantResolver {

    public static final String TENANT_HEADER = "X-Tenant-ID";
    public static final String TENANT_HEADER_LEGACY = "X-Tenant";

    private static final Pattern TENANT_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_-]+$");

    public String resolveTenant(HttpServletRequest request) {

        String tenant = request.getHeader(TENANT_HEADER);
        
        // Fallback para header legado se X-Tenant-ID não estiver presente
        if ((tenant == null || tenant.isBlank())) {
            tenant = request.getHeader(TENANT_HEADER_LEGACY);
        }

        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Header 'X-Tenant-ID' ou 'X-Tenant' é obrigatório"
            );
        }

        if (!TENANT_PATTERN.matcher(tenant).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identificador de tenant inválido"
            );
        }

        return tenant;
    }
}