/* ======================================================
   V43__enhance_email_events.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

ALTER TABLE public.email_events
ADD COLUMN subject VARCHAR(255);

ALTER TABLE public.email_events
ADD COLUMN html_content TEXT;

ALTER TABLE public.email_events
ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED';

ALTER TABLE public.email_events
ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.email_events
ADD COLUMN max_attempts INTEGER NOT NULL DEFAULT 5;

ALTER TABLE public.email_events
ADD COLUMN next_retry_at TIMESTAMPTZ;

ALTER TABLE public.email_events
ADD COLUMN processed_at TIMESTAMPTZ;

ALTER TABLE public.email_events
ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_email_events_status_retry
    ON public.email_events (status, next_retry_at);