/* ======================================================
   V19__create_payment_checkout_requests.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.payment_checkout_requests (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    reference_id VARCHAR(255) NOT NULL,

    provider VARCHAR(100) NOT NULL CHECK (
        provider IN ('STRIPE', 'MERCADO_PAGO', 'PAYPAL', 'UNKNOWN')
    ),

    email VARCHAR(150) NOT NULL,

    nome_vendedor VARCHAR(120) NOT NULL,
    whatsapp_vendedor VARCHAR(20) NOT NULL,
    nome_empresa VARCHAR(120),

    slug VARCHAR(80) NOT NULL,

    status VARCHAR(40) NOT NULL CHECK (
        status IN ('PENDING', 'CREATED', 'EXPIRED', 'COMPLETED', 'FAILED')
    ),

    tenant_id UUID NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payment_checkout_reference
        UNIQUE (reference_id),

    CONSTRAINT fk_payment_checkout_requests_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

-- suporte / retries
CREATE INDEX idx_payment_checkout_email_status
    ON public.payment_checkout_requests (email, status);

-- lookup por slug (onboarding)
CREATE INDEX idx_payment_checkout_slug
    ON public.payment_checkout_requests (slug);

-- queries cronológicas
CREATE INDEX idx_payment_checkout_created_at
    ON public.payment_checkout_requests (created_at);

-- opcional: isolamento por tenant
CREATE INDEX idx_payment_checkout_tenant
    ON public.payment_checkout_requests (tenant_id);