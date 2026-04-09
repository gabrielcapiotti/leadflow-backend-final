/* ======================================================
   V6__create_roles.sql

   Cria a tabela roles no schema atual do tenant.
   Depende de search_path configurado pelo TenantProvisioningService.
   ====================================================== */

CREATE TABLE roles (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(50) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_roles_name UNIQUE (name)
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_roles_name
    ON roles (name);

CREATE INDEX idx_roles_created_at
    ON roles (created_at);