/* ======================================================
   V24__create_users.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.users (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(255),

    email VARCHAR(255) NOT NULL,

    password_hash VARCHAR(255) NOT NULL,

    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',

    role_id UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    failed_attempts INTEGER NOT NULL DEFAULT 0,
    lock_until TIMESTAMPTZ,
    credentials_updated_at TIMESTAMPTZ,

    CONSTRAINT uq_users_email_tenant UNIQUE (email, tenant_id),
    CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id)
        REFERENCES public.roles(id)
        ON DELETE RESTRICT
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_users_deleted_at
    ON public.users (deleted_at);

CREATE INDEX idx_users_lock_until
    ON public.users (lock_until);

CREATE INDEX idx_users_tenant
    ON public.users (tenant_id);

-- ✅ CONSOLIDADO DE V84
CREATE INDEX idx_users_tenant_email
    ON public.users (tenant_id, email);