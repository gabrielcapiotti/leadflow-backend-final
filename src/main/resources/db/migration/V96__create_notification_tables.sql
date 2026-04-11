/* ======================================================
   V96__create_notification_tables.sql (DETERMINISTIC)
   ====================================================== */

CREATE TABLE public.notification_history (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,
    recipient_id UUID NOT NULL,

    type VARCHAR(30) NOT NULL,
    template_name VARCHAR(50) NOT NULL,

    subject VARCHAR(200),
    message TEXT,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    sent_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,

    error_message TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE
);

-- ======================================================
-- INDEXES
-- ======================================================

CREATE INDEX idx_notification_tenant
    ON public.notification_history (tenant_id);

CREATE INDEX idx_notification_recipient
    ON public.notification_history (recipient_id);

CREATE INDEX idx_notification_tenant_recipient
    ON public.notification_history (tenant_id, recipient_id);

CREATE INDEX idx_notification_created
    ON public.notification_history (tenant_id, created_at DESC);

CREATE INDEX idx_notification_unread
    ON public.notification_history (tenant_id, read_at)
    WHERE read_at IS NULL;

CREATE INDEX idx_notification_status
    ON public.notification_history (status);

-- ======================================================

CREATE TABLE public.notification_preferences (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,

    email_enabled BOOLEAN NOT NULL DEFAULT true,
    sms_enabled BOOLEAN NOT NULL DEFAULT false,
    push_enabled BOOLEAN NOT NULL DEFAULT false,

    alert_frequency VARCHAR(20) NOT NULL DEFAULT 'realtime',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_prefs_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_prefs_user
        FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_prefs_user_tenant
        UNIQUE (tenant_id, user_id)
);

-- INDEXES
CREATE INDEX idx_notification_prefs_tenant
    ON public.notification_preferences (tenant_id);

CREATE INDEX idx_notification_prefs_user
    ON public.notification_preferences (user_id);