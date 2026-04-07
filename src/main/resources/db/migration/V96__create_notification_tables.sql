-- V96__create_notification_tables.sql
-- Criar tabelas para sistema de notificações

CREATE TABLE IF NOT EXISTS notification_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    template_name VARCHAR(50) NOT NULL,
    subject VARCHAR(200),
    message TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_notification_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES "user"(id),
    CONSTRAINT check_notification_type CHECK (type IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT check_notification_status CHECK (status IN ('SENT', 'PENDING', 'FAILED', 'DELIVERED'))
);

CREATE INDEX IF NOT EXISTS idx_notification_tenant_id ON notification_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_notification_recipient_id ON notification_history(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notification_tenant_recipient ON notification_history(tenant_id, recipient_id);
CREATE INDEX IF NOT EXISTS idx_notification_read_at ON notification_history(read_at);
CREATE INDEX IF NOT EXISTS idx_notification_status ON notification_history(status);

CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL UNIQUE,
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    sms_enabled BOOLEAN NOT NULL DEFAULT false,
    push_enabled BOOLEAN NOT NULL DEFAULT false,
    alert_frequency VARCHAR(20) NOT NULL DEFAULT 'realtime',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_prefs_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_prefs_user FOREIGN KEY (user_id) REFERENCES "user"(id),
    CONSTRAINT check_alert_frequency CHECK (alert_frequency IN ('realtime', 'daily', 'weekly'))
);

CREATE INDEX IF NOT EXISTS idx_notification_prefs_tenant_id ON notification_preferences(tenant_id);
CREATE INDEX IF NOT EXISTS idx_notification_prefs_user_id ON notification_preferences(user_id);
