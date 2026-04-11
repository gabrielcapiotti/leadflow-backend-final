/* ======================================================
   V3__create_tenants.sql (DETERMINISTIC - NON-IDEMPOTENT)

   CREATE TENANTS TABLE (UUID-BASED)

   Responsabilidades:
   - Definir tenants como entidade lógica (UUID)
   - Garantir integridade, auditoria e lifecycle
   - Executar exatamente uma vez (Flyway)
   ====================================================== */

-- ======================================================
-- TABLE
-- ======================================================

CREATE TABLE public.tenants (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL UNIQUE,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT chk_tenants_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

-- ======================================================
-- INDEXES
-- ======================================================

CREATE INDEX idx_tenants_deleted_at
    ON public.tenants (deleted_at);

CREATE INDEX idx_tenants_created_at
    ON public.tenants (created_at);

CREATE INDEX idx_tenants_status
    ON public.tenants (status);

-- ======================================================
-- FUNCTION
-- ======================================================

CREATE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ======================================================
-- TRIGGER
-- ======================================================

CREATE TRIGGER trg_set_updated_at
BEFORE UPDATE ON public.tenants
FOR EACH ROW
EXECUTE FUNCTION public.set_updated_at();