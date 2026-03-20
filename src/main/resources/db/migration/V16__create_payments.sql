/* ======================================================
   PAYMENTS
   Stores payment records from various gateways
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.payments (

    id UUID NOT NULL,

    event_id VARCHAR(255) NOT NULL,

    email VARCHAR(255) NOT NULL,

    status VARCHAR(50) NOT NULL,

    gateway VARCHAR(50) NOT NULL,

    payload TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_payments
        PRIMARY KEY (id),

    CONSTRAINT uq_payments_event_id
        UNIQUE (event_id)
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX IF NOT EXISTS idx_payment_event
    ON public.payments (event_id);

CREATE INDEX IF NOT EXISTS idx_payment_email
    ON public.payments (email);

CREATE INDEX IF NOT EXISTS idx_payment_status
    ON public.payments (status);

CREATE INDEX IF NOT EXISTS idx_payment_gateway
    ON public.payments (gateway);

CREATE INDEX IF NOT EXISTS idx_payment_created_at
    ON public.payments (created_at);
