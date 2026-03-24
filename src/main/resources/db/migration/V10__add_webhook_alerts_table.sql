CREATE TABLE IF NOT EXISTS webhook_alerts (
    id UUID PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    tenant_id UUID NOT NULL,
    message TEXT,
    metrics JSONB,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Indices for querying alerts
CREATE INDEX IF NOT EXISTS idx_webhook_alerts_tenant_created 
    ON webhook_alerts(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_webhook_alerts_severity_created 
    ON webhook_alerts(severity, created_at DESC);

-- Index for finding active (unresolved) alerts
CREATE INDEX IF NOT EXISTS idx_webhook_alerts_active 
    ON webhook_alerts(resolved_at) 
    WHERE resolved_at IS NULL;

-- Performance index for dashboard queries
CREATE INDEX IF NOT EXISTS idx_webhook_alerts_created 
    ON webhook_alerts(created_at DESC);
