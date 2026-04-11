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

/* ======================================================
   SEED ROLES (PADRÃO PARA TODOS OS TENANTS)
   Os 3 roles padrão com IDs determinísticos
   ====================================================== */

INSERT INTO roles (id, name, created_at, updated_at) VALUES
    ('45c9118e-0c84-4669-85f3-91abcb6d09c8'::uuid, 'ROLE_USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('3227b3fd-0166-43d0-be36-607627caf026'::uuid, 'ROLE_ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('55099259-9488-468f-bdbf-0ebdb0c0c848'::uuid, 'ROLE_VENDOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;