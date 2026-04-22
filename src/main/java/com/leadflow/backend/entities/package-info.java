/**
 * Entity package with centralized multi-tenant filter definition.
 * 
 * All tenant-aware entities use:
 * - Centralized @FilterDef("tenantFilter") defined here
 * - Individual @Filter annotations on each entity
 * - TenantContext ThreadLocal to manage current tenant context
 */
@org.hibernate.annotations.FilterDefs({
        @org.hibernate.annotations.FilterDef(
                name = "tenantFilter",
                parameters = @org.hibernate.annotations.ParamDef(name = "tenantId", type = String.class)
        )
})
package com.leadflow.backend.entities;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterDefs;
import org.hibernate.annotations.ParamDef;
