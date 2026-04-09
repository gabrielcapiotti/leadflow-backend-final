/* ======================================================
   V16__create_payments.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.payments (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    event_id VARCHAR(255) NOT NULL,

    email VARCHAR(255) NOT NULL,

    status VARCHAR(50) NOT NULL CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED')
    ),

    gateway VARCHAR(50) NOT NULL CHECK (
        gateway IN ('STRIPE', 'MERCADO_PAGO', 'PAYPAL', 'UNKNOWN')
    ),

    payload TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payments_event_id
        UNIQUE (event_id)
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_payments_event
    ON public.payments (event_id);

CREATE INDEX idx_payments_email
    ON public.payments (email);

CREATE INDEX idx_payments_status
    ON public.payments (status);

CREATE INDEX idx_payments_gateway
    ON public.payments (gateway);

CREATE INDEX idx_payments_created_at
    ON public.payments (created_at);