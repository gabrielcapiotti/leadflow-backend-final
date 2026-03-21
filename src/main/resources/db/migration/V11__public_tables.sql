/* ======================================================
   GLOBAL TABLES (PUBLIC SCHEMA)
   ====================================================== */

-- ======================================================
-- TENANTS
-- ======================================================

CREATE TABLE IF NOT EXISTS public.tenants (

    id UUID NOT NULL,

    name VARCHAR(100) NOT NULL,
    schema_name VARCHAR(100) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- PRIMARY KEY (idempotente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'pk_tenants'
    ) THEN
        ALTER TABLE public.tenants
        ADD CONSTRAINT pk_tenants PRIMARY KEY (id);
    END IF;
END $$;

-- UNIQUE CONSTRAINTS (idempotentes)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_tenants_name'
    ) THEN
        ALTER TABLE public.tenants
        ADD CONSTRAINT uq_tenants_name UNIQUE (name);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_tenants_schema_name'
    ) THEN
        ALTER TABLE public.tenants
        ADD CONSTRAINT uq_tenants_schema_name UNIQUE (schema_name);
    END IF;
END $$;

-- INDEXES
CREATE INDEX IF NOT EXISTS idx_tenants_schema_name
    ON public.tenants (schema_name);

CREATE INDEX IF NOT EXISTS idx_tenants_deleted_at
    ON public.tenants (deleted_at);



-- ======================================================
-- GLOBAL ROLES
-- ======================================================

CREATE TABLE IF NOT EXISTS public.roles (

    id UUID NOT NULL,

    name VARCHAR(50) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- PRIMARY KEY (idempotente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'pk_roles'
    ) THEN
        ALTER TABLE public.roles
        ADD CONSTRAINT pk_roles PRIMARY KEY (id);
    END IF;
END $$;

-- UNIQUE (idempotente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_roles_name'
    ) THEN
        ALTER TABLE public.roles
        ADD CONSTRAINT uq_roles_name UNIQUE (name);
    END IF;
END $$;

-- INDEXES
CREATE INDEX IF NOT EXISTS idx_roles_name
    ON public.roles (name);