-- Create table for failed webhook events
CREATE TABLE IF NOT EXISTS public.failed_webhook_events (
    id VARCHAR(36) PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    event_data TEXT NOT NULL,
    failure_reason TEXT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED_PERMANENT')),
    next_retry_at TIMESTAMP NOT NULL,
    original_received_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    succeeded_at TIMESTAMP,
    tenant_id VARCHAR(255)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_status ON public.failed_webhook_events (status);
CREATE INDEX IF NOT EXISTS idx_created_at ON public.failed_webhook_events (created_at);
CREATE INDEX IF NOT EXISTS idx_next_retry ON public.failed_webhook_events (next_retry_at);
