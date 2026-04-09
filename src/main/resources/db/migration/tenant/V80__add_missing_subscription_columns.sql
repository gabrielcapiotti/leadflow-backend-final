-- Direct conversion (will fail if columns don't exist - correct)
ALTER TABLE public.subscriptions
ALTER COLUMN last_payment_date TYPE TIMESTAMPTZ
USING last_payment_date AT TIME ZONE 'UTC';

ALTER TABLE public.subscriptions
ALTER COLUMN cancelled_at TYPE TIMESTAMPTZ
USING cancelled_at AT TIME ZONE 'UTC';