/* ======================================================
   V8__create_vendors.sql
   VENDORS (TENANT = ROOT ENTITY)

   Estrutura consolidada, declarativa e alinhada com Entity
   ====================================================== */

CREATE TABLE vendors (

    /* ========== IDENTIDADE ========== */

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    /* ========== DADOS DO VENDOR ========== */

    name VARCHAR(255) NOT NULL,
    description TEXT,

    -- 🔵 ADICIONADOS (ENTITY-DRIVEN)
    nome_empresa VARCHAR(255),
    nome_vendedor VARCHAR(255),

    /* ========== STATUS ========== */

    active BOOLEAN NOT NULL DEFAULT TRUE,

    /* ========== BILLING ========== */

    external_customer_id VARCHAR(255) UNIQUE,
    external_subscription_id VARCHAR(255),
    next_billing_at TIMESTAMPTZ,
    last_payment_at TIMESTAMPTZ,

    /* ========== SUBSCRIPTION ========== */

    subscription_status VARCHAR(32) NOT NULL DEFAULT 'TRIAL',
    subscription_started_at TIMESTAMPTZ,
    subscription_expires_at TIMESTAMPTZ,
    subscription_plan VARCHAR(50),
    auto_renewal BOOLEAN NOT NULL DEFAULT TRUE,
    billing_amount DECIMAL(10, 2),
    payment_method_id VARCHAR(255),

    /* ========== CANCELLATION ========== */

    cancellation_requested_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(255),

    /* ========== IDENTIDADE EXTERNA / APP ========== */

    slug VARCHAR(255) UNIQUE,
    user_email VARCHAR(255),
    logo_url VARCHAR(255),

    /* ========== BRANDING / CONTACT ========== */

    cor_destaque VARCHAR(20),
    mensagem_boas_vindas TEXT,
    whatsapp_vendedor VARCHAR(50),

    /* ========== AUDITORIA ========== */

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    /* ========== CONSTRAINTS ========== */

    CONSTRAINT chk_subscription_status CHECK (
        subscription_status IN (
            'TRIAL',
            'TRIALING',
            'ACTIVE',
            'PAST_DUE',
            'CANCELED',
            'EXPIRED'
        )
    )
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_vendors_name
    ON vendors (name);

CREATE INDEX idx_vendors_active
    ON vendors (active);

CREATE INDEX idx_vendors_deleted_at
    ON vendors (deleted_at);

CREATE INDEX idx_vendors_created_at
    ON vendors (created_at);

/* ===== BILLING ===== */

CREATE INDEX idx_vendors_external_customer_id
    ON vendors (external_customer_id);

CREATE INDEX idx_vendors_external_subscription_id
    ON vendors (external_subscription_id);

CREATE INDEX idx_vendors_next_billing_at
    ON vendors (next_billing_at);

CREATE INDEX idx_vendors_last_payment_at
    ON vendors (last_payment_at);

/* ===== SUBSCRIPTION ===== */

CREATE INDEX idx_vendors_subscription_status
    ON vendors (subscription_status);

CREATE INDEX idx_vendors_subscription_started_at
    ON vendors (subscription_started_at);

CREATE INDEX idx_vendors_subscription_expires_at
    ON vendors (subscription_expires_at);

CREATE INDEX idx_vendors_subscription_plan
    ON vendors (subscription_plan);

CREATE INDEX idx_vendors_auto_renewal
    ON vendors (auto_renewal);

CREATE INDEX idx_vendors_cancellation_requested_at
    ON vendors (cancellation_requested_at);

/* ===== APP / IDENTIDADE ===== */

CREATE INDEX idx_vendors_slug
    ON vendors (slug);

CREATE INDEX idx_vendors_user_email
    ON vendors (user_email);