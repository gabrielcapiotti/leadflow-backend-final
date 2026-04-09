/* ======================================================
   V3__create_tenants.sql

   CREATE TENANTS TABLE (PUBLIC SCHEMA)

   Responsabilidades:
   - Definir tenants globais (multi-tenant schema-based)
   - Garantir integridade, auditoria e lifecycle
   ====================================================== */

CREATE TABLE public.tenants (

    /* ========== IDENTIDADE ========== */

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    /* ========== DADOS DO TENANT ========== */

    name VARCHAR(100) NOT NULL,
    schema_name VARCHAR(100) NOT NULL,

    /* ========== STATUS / LIFECYCLE ========== */

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    /* ========== AUDITORIA ========== */

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    /* ========== CONSTRAINTS ========== */

    CONSTRAINT uq_tenants_name UNIQUE (name),
    CONSTRAINT uq_tenants_schema_name UNIQUE (schema_name),

    -- validação de formato do schema (lowercase + snake_case)
    CONSTRAINT chk_tenants_schema_name
        CHECK (schema_name ~ '^[a-z0-9_]+$'),

    -- validação de status permitido (evita lixo semântico)
    CONSTRAINT chk_tenants_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

/* ======================================================
   INDEXES
   ====================================================== */

-- lookup principal (tenant resolver)
CREATE INDEX idx_tenants_schema_name
    ON public.tenants (schema_name);

-- soft delete queries
CREATE INDEX idx_tenants_deleted_at
    ON public.tenants (deleted_at);

-- auditoria / ordenação
CREATE INDEX idx_tenants_created_at
    ON public.tenants (created_at);

-- status filtering (billing, ativação, etc.)
CREATE INDEX idx_tenants_status
    ON public.tenants (status);

/* ======================================================
   TRIGGER: AUTO UPDATE updated_at
   ====================================================== */

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_set_updated_at
BEFORE UPDATE ON public.tenants
FOR EACH ROW
EXECUTE FUNCTION public.set_updated_at();