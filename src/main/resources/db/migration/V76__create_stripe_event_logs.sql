-- Create table for storing Stripe webhook events
CREATE TABLE IF NOT EXISTS public.stripe_event_logs (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'RETRY_PENDING')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP(6),
    last_error TEXT,
    processed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_event_id ON public.stripe_event_logs (event_id);
CREATE INDEX IF NOT EXISTS idx_event_type ON public.stripe_event_logs (event_type);
CREATE INDEX IF NOT EXISTS idx_status ON public.stripe_event_logs (status);
CREATE INDEX IF NOT EXISTS idx_created_at ON public.stripe_event_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_next_retry_at ON public.stripe_event_logs (next_retry_at) WHERE status IN ('PENDING', 'RETRY_PENDING');
